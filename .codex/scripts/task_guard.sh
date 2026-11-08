#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  task_guard.sh start --id ID --mode MODE --scope PATH [--scope PATH ...]
                      [--adopt-dirty PATH ...]
  task_guard.sh check
  task_guard.sh finish --verified EVIDENCE --commit-message MESSAGE
  task_guard.sh abort
  task_guard.sh status

Modes: quick-fix, standard, assessment, upstream

Exit codes: 0 pass, 2 usage/setup error,
4 safety gate (scope, dirty-file, branch, or HEAD violation),
6 commit created but recovery-tag creation needs a direct fix and one retry.
EOF
}

fail_usage() {
  printf 'ERROR: %s\n' "$1" >&2
  usage >&2
  exit 2
}

safety_gate() {
  printf 'SAFETY_GATE: %s\n' "$1" >&2
  exit 4
}

repo_root="$(git rev-parse --show-toplevel 2>/dev/null)" || fail_usage "not inside a Git worktree"
cd "$repo_root"

state_root="$repo_root/.codex/task-state"
state_dir="$state_root/current"

normalize_path() {
  local value="$1"
  while [[ "$value" == ./* ]]; do value="${value#./}"; done
  while [[ "$value" == */ && "$value" != "/" ]]; do value="${value%/}"; done
  case "$value" in
    ""|.|/|/*|..|../*|*/../*|*/..)
      return 1
      ;;
  esac
  printf '%s\n' "$value"
}

path_matches_file() {
  local path="$1"
  local list_file="$2"
  local pattern
  [[ -f "$list_file" ]] || return 1
  while IFS= read -r pattern; do
    [[ -n "$pattern" ]] || continue
    if [[ "$path" == "$pattern" || "$path" == "$pattern"/* ]]; then
      return 0
    fi
  done < "$list_file"
  return 1
}

list_dirty() {
  {
    git diff --name-only
    git diff --cached --name-only
    git ls-files --others --exclude-standard
  } | LC_ALL=C sort -u
}

list_staged() {
  git diff --cached --name-only | LC_ALL=C sort -u
}

check_staged_whitespace() {
  local -a paths=()
  local path
  while IFS= read -r path; do
    [[ -n "$path" ]] || continue
    [[ "$path" == *.patch ]] && continue
    paths+=("$path")
  done < <(list_staged)
  ((${#paths[@]} == 0)) && return 0
  # Windows CreateProcess 32KB argv 上限：大合并时一次性传全部 staged 路径会 E2BIG。
  # 分批校验保持语义不变（.patch 排除已在上方完成）。
  local -a chunk=()
  local i
  for ((i = 0; i < ${#paths[@]}; i += 100)); do
    chunk=("${paths[@]:i:100}")
    git diff --cached --check -- "${chunk[@]}"
  done
}

fingerprint_path() {
  local path="$1"
  local work_hash="MISSING"
  local index_hash
  local status_hash
  if [[ -e "$path" || -L "$path" ]]; then
    work_hash="$(git hash-object --no-filters -- "$path" 2>/dev/null || printf 'UNHASHABLE')"
  fi
  index_hash="$(git ls-files -s -- "$path" | git hash-object --stdin)"
  status_hash="$(git status --porcelain=v1 -- "$path" | git hash-object --stdin)"
  printf '%s\n%s\n%s\n' "$work_hash" "$index_hash" "$status_hash" | git hash-object --stdin
}

read_state() {
  if [[ -f "$state_dir/$1" ]]; then
    sed -n '1p' "$state_dir/$1"
  else
    return 1
  fi
}

write_state() {
  printf '%s\n' "$2" > "$state_dir/$1"
}

show_status() {
  [[ -d "$state_dir" ]] || fail_usage "no task guard state"
  printf 'Task: %s\n' "$(read_state id)"
  printf 'Mode: %s\n' "$(read_state mode)"
  printf 'Status: %s\n' "$(read_state status)"
  printf 'Branch: %s\n' "$(read_state branch)"
  printf 'Base HEAD: %s\n' "$(read_state base_head)"
  printf '%s\n' 'Scope:'
  sed 's/^/  - /' "$state_dir/scope"
  if [[ -s "$state_dir/adopted" ]]; then
    printf '%s\n' 'Explicitly adopted initial dirty paths:'
    sed 's/^/  - /' "$state_dir/adopted"
  fi
}

scope_check() {
  local expected_branch
  local expected_head
  local current_branch
  local current_head
  local path
  local saved_hash
  local current_hash
  local violations=0

  [[ "$(read_state status)" == "active" ]] || fail_usage "task is not active"
  expected_branch="$(read_state branch)"
  expected_head="$(read_state base_head)"
  current_branch="$(git branch --show-current)"
  current_head="$(git rev-parse HEAD)"

  [[ "$current_branch" == "$expected_branch" ]] || safety_gate "branch changed: expected $expected_branch, got $current_branch"
  [[ "$current_head" == "$expected_head" ]] || safety_gate "HEAD changed outside task_guard finish: expected $expected_head, got $current_head"

  while IFS=$'\t' read -r path saved_hash; do
    [[ -n "$path" ]] || continue
    current_hash="$(fingerprint_path "$path")"
    if [[ "$current_hash" != "$saved_hash" ]]; then
      printf 'SCOPE_VIOLATION: protected initial dirty path changed: %s\n' "$path" >&2
      violations=$((violations + 1))
    fi
  done < "$state_dir/protected-fingerprints"

  while IFS= read -r path; do
    [[ -n "$path" ]] || continue
    if path_matches_file "$path" "$state_dir/protected"; then
      continue
    fi
    if ! path_matches_file "$path" "$state_dir/scope"; then
      printf 'SCOPE_VIOLATION: changed path is outside declared scope: %s\n' "$path" >&2
      violations=$((violations + 1))
      continue
    fi
  done < <(list_dirty)

  while IFS= read -r path; do
    [[ -n "$path" ]] || continue
    if path_matches_file "$path" "$state_dir/protected" || ! path_matches_file "$path" "$state_dir/scope"; then
      printf 'SCOPE_VIOLATION: staged path is not task-owned: %s\n' "$path" >&2
      violations=$((violations + 1))
    fi
  done < <(list_staged)

  if ((violations > 0)); then
    safety_gate "$violations scope/worktree violation(s); reconcile the declared scope or worktree before continuing"
  fi

  printf 'PASS: task=%s branch/HEAD, protected dirty paths, scope, and staged paths are safe\n' \
    "$(read_state id)"
}

start_task() {
  local id=""
  local mode=""
  local -a scopes=()
  local -a adopted=()
  local value

  while (($# > 0)); do
    case "$1" in
      --id) (($# >= 2)) || fail_usage "--id needs a value"; id="$2"; shift 2 ;;
      --mode) (($# >= 2)) || fail_usage "--mode needs a value"; mode="$2"; shift 2 ;;
      --scope) (($# >= 2)) || fail_usage "--scope needs a value"; scopes+=("$2"); shift 2 ;;
      --adopt-dirty) (($# >= 2)) || fail_usage "--adopt-dirty needs a value"; adopted+=("$2"); shift 2 ;;
      *) fail_usage "unknown start option: $1" ;;
    esac
  done

  [[ "$id" =~ ^[A-Za-z0-9._-]+$ ]] || fail_usage "task id must use letters, digits, dot, underscore, or hyphen"
  [[ ${#scopes[@]} -gt 0 ]] || fail_usage "at least one --scope is required"
  case "$mode" in
    quick-fix|standard|assessment|upstream) ;;
    *) fail_usage "unknown mode: $mode" ;;
  esac

  mkdir -p "$state_dir"
  if [[ -d "$state_dir" && -f "$state_dir/status" ]]; then
    local previous_status
    local previous_id
    previous_status="$(sed -n '1p' "$state_dir/status")"
    previous_id="$(sed -n '1p' "$state_dir/id" 2>/dev/null || true)"
    if [[ "$previous_status" == "active" || "$previous_status" == "commit_needs_tag" ]]; then
      # 只把「有 id 的未完成会话」当作真实占用。写序修复前留下的半成品状态
      # （有 status 无 id）必须被识别为损坏，否则它会永久堵死后续所有 start。
      if [[ -z "$previous_id" ]]; then
        fail_usage "task guard state is corrupt (status=$previous_status but no id); run 'task_guard.sh abort' to archive it"
      fi
      fail_usage "another task guard is unfinished: $previous_id ($previous_status)"
    fi
  fi
  # 会话初始化。status 一律在 start_task 末尾写入：任何中途失败（scope 校验、
  # --adopt-dirty 校验等）都不得留下「active 但无 id」的会话。
  : > "$state_dir/initial-dirty"
  : > "$state_dir/initial-staged"
  : > "$state_dir/protected"
  : > "$state_dir/protected-fingerprints"
  : > "$state_dir/scope"
  : > "$state_dir/adopted"

  for value in "${scopes[@]}"; do
    value="$(normalize_path "$value")" || fail_usage "unsafe scope path: $value"
    printf '%s\n' "$value" >> "$state_dir/scope"
  done
  LC_ALL=C sort -u "$state_dir/scope" -o "$state_dir/scope"

  for value in "${adopted[@]-}"; do
    [[ -n "$value" ]] || continue
    value="$(normalize_path "$value")" || fail_usage "unsafe adopted path: $value"
    if ! path_matches_file "$value" "$state_dir/scope"; then
      fail_usage "adopted path is outside scope: $value"
    fi
    printf '%s\n' "$value" >> "$state_dir/adopted"
  done
  LC_ALL=C sort -u "$state_dir/adopted" -o "$state_dir/adopted"

  list_dirty > "$state_dir/initial-dirty"
  list_staged > "$state_dir/initial-staged"
  local adopted_match
  while IFS= read -r value; do
    [[ -n "$value" ]] || continue
    if path_matches_file "$value" "$state_dir/scope" && ! path_matches_file "$value" "$state_dir/adopted"; then
      fail_usage "scope overlaps pre-existing dirty path without --adopt-dirty: $value"
    fi
  done < "$state_dir/initial-dirty"

  while IFS= read -r value; do
    [[ -n "$value" ]] || continue
    adopted_match=0
    local dirty_path
    while IFS= read -r dirty_path; do
      if [[ "$dirty_path" == "$value" || "$dirty_path" == "$value"/* ]]; then
        adopted_match=1
        break
      fi
    done < "$state_dir/initial-dirty"
    ((adopted_match == 1)) || fail_usage "--adopt-dirty path has no initial dirty match: $value"
  done < "$state_dir/adopted"

  while IFS= read -r value; do
    [[ -n "$value" ]] || continue
    if ! path_matches_file "$value" "$state_dir/adopted"; then
      fail_usage "pre-existing staged path would contaminate an automatic commit: $value"
    fi
  done < "$state_dir/initial-staged"

  : > "$state_dir/protected"
  : > "$state_dir/protected-fingerprints"
  while IFS= read -r value; do
    [[ -n "$value" ]] || continue
    if ! path_matches_file "$value" "$state_dir/adopted"; then
      printf '%s\n' "$value" >> "$state_dir/protected"
      printf '%s\t%s\n' "$value" "$(fingerprint_path "$value")" >> "$state_dir/protected-fingerprints"
    fi
  done < "$state_dir/initial-dirty"

  write_state id "$id"
  write_state mode "$mode"
  write_state branch "$(git branch --show-current)"
  write_state base_head "$(git rev-parse HEAD)"
  # 最后一步：只有走到这里会话才真正生效。
  write_state status active

  show_status
  printf 'PASS: task guard started; protected %s pre-existing dirty path(s)\n' "$(wc -l < "$state_dir/protected" | tr -d ' ')"
}

# 归档一个未完成的会话，使后续 start 不再被堵死。
#
# 存在的理由：start 在 scope / --adopt-dirty 校验失败时会在写完 id 之前退出。
# 写序已修为「status 最后写」，但历史遗留或异常中断仍可能留下半成品状态，
# 而脚本原本没有任何退路（只能手工 rm -rf），所以 abort 是必要的逃生口。
#
# 安全约束：绝不静默丢弃工作。已提交（commit_needs_tag）的会话必须走 finish，
# 否则会丢掉 recovery tag；归档保留全部状态文件供事后查证。
abort_task() {
  (($# == 0)) || fail_usage "abort accepts no options"
  [[ -d "$state_dir" ]] || fail_usage "no task guard state to abort"

  local status=""
  local id=""
  status="$(sed -n '1p' "$state_dir/status" 2>/dev/null || true)"
  id="$(sed -n '1p' "$state_dir/id" 2>/dev/null || true)"

  if [[ "$status" == "commit_needs_tag" ]]; then
    fail_usage "session $id already committed; run 'finish' to create its recovery tag instead of aborting"
  fi

  local label="${id:-corrupt}"
  local archive
  archive="$state_root/aborted-$(TZ=Asia/Shanghai date +%Y%m%d%H%M%S)-$label"
  mv "$state_dir" "$archive"
  printf 'PASS: aborted session %s (status=%s); state archived at %s\n' \
    "$label" "${status:-none}" "$(basename "$archive")"
  printf 'NOTE: worktree changes were left untouched.\n'
}

create_recovery_tag() {
  local commit="$1"
  local verified="$2"
  local stamp
  local tag
  local tag_started
  local tag_finished
  local tag_elapsed

  stamp="$(TZ=Asia/Shanghai date +%Y%m%d%H%M%S)"
  tag="recovery/$(read_state id)/$stamp-${commit:0:12}"
  tag_started="$(date +%s)"
  write_state pending_tag "$tag"
  if ! GIT_OPTIONAL_LOCKS=0 git -c tag.gpgSign=false tag -a "$tag" "$commit" -m "Recovery point for $(read_state id). Verification: $verified"; then
    write_state status commit_needs_tag
    printf 'TAG_NEEDS_DIRECT_FIX: commit %s is safe; tag command failed once and was not repeated. Fix the reported cause, then rerun finish.\n' "$commit" >&2
    exit 6
  fi
  tag_finished="$(date +%s)"
  tag_elapsed="$((tag_finished - tag_started))"
  write_state tag_elapsed_seconds "$tag_elapsed"
  write_state recovery_tag "$tag"
  write_state status finished
  mkdir -p "$state_root"
  mv "$state_dir" "$state_root/finished-$(TZ=Asia/Shanghai date +%Y%m%d%H%M%S)-$(read_state id)"
  if ((tag_elapsed > 5)); then
    printf 'WARN: recovery tag phase took %ss (target <=5s); tag already created, continue without repeated validation\n' "$tag_elapsed" >&2
  fi
  printf 'PASS: annotated recovery tag %s (%ss)\n' "$tag" "$tag_elapsed"
}

finish_task() {
  local verified=""
  local commit_message=""
  while (($# > 0)); do
    case "$1" in
      --verified) (($# >= 2)) || fail_usage "--verified needs a value"; verified="$2"; shift 2 ;;
      --commit-message) (($# >= 2)) || fail_usage "--commit-message needs a value"; commit_message="$2"; shift 2 ;;
      *) fail_usage "unknown finish option: $1" ;;
    esac
  done
  [[ -n "$verified" && -n "$commit_message" ]] || fail_usage "verification evidence and commit message are required"

  local committed_head
  committed_head="$(read_state committed_head 2>/dev/null || true)"
  if [[ "$(read_state status)" == "commit_needs_tag" ]] ||
     { [[ -n "$committed_head" ]] && [[ "$(git rev-parse HEAD)" == "$committed_head" ]]; }; then
    create_recovery_tag "$(read_state committed_head)" "$(read_state verification)"
    return
  fi
  scope_check

  local path
  local task_change_count=0
  while IFS= read -r path; do
    [[ -n "$path" ]] || continue
    path_matches_file "$path" "$state_dir/protected" && continue
    path_matches_file "$path" "$state_dir/scope" || continue
    if [[ -e "$path" || -L "$path" ]]; then
      git add -A -- "$path"
    else
      git update-index --remove -- "$path"
    fi
    task_change_count=$((task_change_count + 1))
  done < <(list_dirty)
  ((task_change_count > 0)) || safety_gate "no task-owned changes to commit"

  check_staged_whitespace || safety_gate "staged non-patch diff failed whitespace validation"
  local staged_count=0
  while IFS= read -r path; do
    [[ -n "$path" ]] || continue
    staged_count=$((staged_count + 1))
    if ! path_matches_file "$path" "$state_dir/scope" || path_matches_file "$path" "$state_dir/protected"; then
      safety_gate "automatic commit would include non-task path: $path"
    fi
  done < <(list_staged)
  ((staged_count > 0)) || safety_gate "task changes did not produce a staged diff"

  git commit -m "$commit_message" -m "Verification: $verified" -m "Task-Guard: $(read_state id)"
  local commit
  commit="$(git rev-parse HEAD)"
  write_state committed_head "$commit"
  write_state verification "$verified"
  write_state status commit_needs_tag
  printf 'PASS: committed %s\n' "$commit"
  create_recovery_tag "$commit" "$verified"
}

command_name="${1:-}"
[[ -n "$command_name" ]] || { usage; exit 2; }
shift

case "$command_name" in
  start) start_task "$@" ;;
  check) (($# == 0)) || fail_usage "check accepts no options"; scope_check ;;
  finish) finish_task "$@" ;;
  abort) abort_task "$@" ;;
  status) (($# == 0)) || fail_usage "status accepts no options"; show_status ;;
  -h|--help|help) usage ;;
  *) fail_usage "unknown command: $command_name" ;;
esac
