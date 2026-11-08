package com.fongmi.android.tv.player;

import java.util.regex.Pattern;

/**
 * 徽标牌的完整清单：参考实现（xperience-app badges 26cc3192）里全部 127 条牌的图形键（100 条来自参考实现，27 条来自扩充包）、规范名与识别正则。
 * <p>
 * 每一条都按 id 与图片一一对应，图形落在 {@code res/drawable-nodpi/badge_<plate>.webp}，
 * 因此这里是「牌」的唯一事实源：新增或删减牌只需改本表与图片。
 * <p>
 * {@code runtime} 为 true 的组（分辨率、动态范围、编码、音频、声道）优先取播放器运行时事实，
 * 只有在运行时拿不到事实时才回退到文件名匹配；其余组（发布来源、画质、版别、特别标签、IMAX、流媒体、视频技术）
 * 只能由文件名/标题里的字符串证明，运行时事实给不了这些证据。
 */
public final class MediaBadgeCatalog {

    /** 徽标分组，决定配色与展示顺序。 */
    public enum Group {
        RESOLUTION, DYNAMIC_RANGE, VIDEO_TECH, VIDEO_CODEC, AUDIO, CHANNELS, FRAME_RATE, BITRATE, CONTAINER,
        MEDIA_SOURCE, QUALITY, EDITION, SPECIAL_TAG, IMAX, STREAMING
    }

    /**
     * 一条牌的规则。
     *
     * @param group   分组
     * @param plate   图形键，对应 {@code R.drawable.badge_<plate>}
     * @param name    规范名，仅在缺少图形时作为文本兜底
     * @param pattern 识别正则，原样来自参考实现
     * @param runtime 该牌的语义能否由播放器运行时事实证明
     */
    public record Rule(Group group, String plate, String name, String pattern, boolean runtime) {
    }

    private static final Rule[] RULES = {
            new Rule(Group.SPECIAL_TAG, "gst_seadex", "SEADEX", "(?i)\\bseadex\\b", false),
            new Rule(Group.SPECIAL_TAG, "gst_hybrid", "HYBRID", "(?i)(?<![\\s\\S])(?=[\\s\\S]*?(?:(?:(?<![A-Za-z0-9])hybrid(?![A-Za-z0-9]))))", false),
            new Rule(Group.SPECIAL_TAG, "gst_criterion", "CRITERION", "(?i)(?<![\\s\\S])(?=[\\s\\S]*?(?:(?:(?<![A-Za-z0-9])criterion(?:[-_. ]?collection)?(?![A-Za-z0-9]))))", false),
            new Rule(Group.SPECIAL_TAG, "gst_proper", "PROPER", "(?i)(?<![\\s\\S])(?=[\\s\\S]*?(?:(?:(?<![A-Za-z0-9])proper(?:\\d+)?(?![A-Za-z0-9]))))", false),
            new Rule(Group.SPECIAL_TAG, "gst_repack", "REPACK", "(?i)(?<![\\s\\S])(?=[\\s\\S]*?(?:(?:(?<![A-Za-z0-9])repack(?:\\d+)?(?![A-Za-z0-9]))))", false),
            new Rule(Group.SPECIAL_TAG, "gst_remastered", "REMASTERED", "(?i)(?<![\\s\\S])(?=[\\s\\S]*?(?:(?:(?<![A-Za-z0-9])(?:remaster(?:ed)?|rmstrd)(?![A-Za-z0-9]))))", false),
            new Rule(Group.SPECIAL_TAG, "gst_open_matte", "OPEN MATTE", "(?i)(?<![\\s\\S])(?=[\\s\\S]*?(?:(?:(?<![A-Za-z0-9])open[-_. ]?matte(?![A-Za-z0-9]))))", false),
            new Rule(Group.SPECIAL_TAG, "gst_regraded", "REGRADED", "(?i)(?<![\\s\\S])(?=[\\s\\S]*?(?:(?:(?<![A-Za-z0-9])re[-_. ]?graded?(?![A-Za-z0-9]))))", false),
            new Rule(Group.SPECIAL_TAG, "gst_directors_cut", "DIR CUT", "(?i)(?<![\\s\\S])(?=[\\s\\S]*?(?:(?:(?<![A-Za-z0-9])(?:director(?:s|'s)?[-_. ]?cut|dc)(?![A-Za-z0-9]))))", false),
            new Rule(Group.SPECIAL_TAG, "gst_extended", "EXTENDED", "(?i)(?<![\\s\\S])(?=[\\s\\S]*?(?:(?:(?<![A-Za-z0-9])(?:extended(?:[-_. ]?(?:cut|edition))?|ext)(?![A-Za-z0-9]))))", false),
            new Rule(Group.SPECIAL_TAG, "gst_uncut", "UNCUT", "(?i)(?<![\\s\\S])(?=[\\s\\S]*?(?:(?:(?<![A-Za-z0-9])uncut(?![A-Za-z0-9]))))", false),
            new Rule(Group.SPECIAL_TAG, "gst_uncensored", "UNCENSORED", "(?i)(?<![\\s\\S])(?=[\\s\\S]*?(?:(?:(?<![A-Za-z0-9])uncensored(?![A-Za-z0-9]))))", false),
            new Rule(Group.SPECIAL_TAG, "gst_black_and_white", "BLACK & WHITE", "(?i)(?<![\\s\\S])(?=[\\s\\S]*?(?:(?:(?<![A-Za-z0-9])(?:authentic[-_. ]?(?:bw|black[-_. ]?(?:and|&)[-_. ]?white)|bw|b\\s*[&\uff06]\\s*w|black[-_. ]?(?:and|&)[-_. ]?white)(?![A-Za-z0-9]))))(?![\\s\\S]*(?:(?:(?<![A-Za-z0-9])(?:true[-_. ]?hue(?:[-_. ]?(?:full[-_. ]?)?color)?|colorized|full[-_. ]?color)(?![A-Za-z0-9]))))", false),
            new Rule(Group.SPECIAL_TAG, "gst_true_hue", "TRUE-HUE", "(?i)(?<![\\s\\S])(?=[\\s\\S]*?(?:(?:(?<![A-Za-z0-9])(?:true[-_. ]?hue(?:[-_. ]?(?:full[-_. ]?)?color)?|colorized|full[-_. ]?color)(?![A-Za-z0-9]))))(?![\\s\\S]*(?:(?:(?<![A-Za-z0-9])(?:authentic[-_. ]?(?:bw|black[-_. ]?(?:and|&)[-_. ]?white)|bw|b\\s*[&\uff06]\\s*w|black[-_. ]?(?:and|&)[-_. ]?white)(?![A-Za-z0-9]))))", false),
            new Rule(Group.SPECIAL_TAG, "gst_theatrical", "THTR", "(?i)(?<![\\s\\S])(?=[\\s\\S]*?(?:(?:(?<![A-Za-z0-9])(?:theatrical(?:[-_. ]?cut)?|thtr)(?![A-Za-z0-9]))))", false),
            new Rule(Group.EDITION, "ged_cc", "CC标准收藏", "(?i)(?:CC标准收藏|标准收藏|cc版)", false),
            new Rule(Group.EDITION, "ged_hk", "港版", "(?:港版)", false),
            new Rule(Group.EDITION, "ged_tw", "台版", "(?:台版)", false),
            new Rule(Group.EDITION, "ged_jp", "日版", "(?:日版)", false),
            new Rule(Group.EDITION, "ged_us", "美版", "(?:美版)", false),
            new Rule(Group.EDITION, "ged_uk", "英版", "(?:英版)", false),
            new Rule(Group.EDITION, "ged_de", "德版", "(?:德版)", false),
            new Rule(Group.EDITION, "ged_fr", "法版", "(?:法版)", false),
            new Rule(Group.EDITION, "ged_eu", "欧版", "(?:欧版)", false),
            new Rule(Group.EDITION, "ged_kr", "韩版", "(?:韩版)", false),
            new Rule(Group.EDITION, "ged_arrow", "箭影", "(?:箭影(?:版)?)", false),
            new Rule(Group.EDITION, "ged_dieying", "碟影", "(?:碟影(?:版)?)", false),
            new Rule(Group.EDITION, "ged_allregion", "全区版", "(?:全区版|国际版)", false),
            new Rule(Group.MEDIA_SOURCE, "gms_remux_1", "REMUX 1", "(?i)(?<![\\s\\S])(?=[\\s\\S]*?(?:(?<![A-Za-z0-9])(?:(?:(?:bd|uhd)[-_. ]?)?remux|bdremux|uhdremux)(?![A-Za-z0-9])))(?=[\\s\\S]*?(?:(?:^|[\\s._-])(?:[\\[(])?(?:BiZKiT|BLURANiUM|CiNEPHiLES|FraMeSToR|PiRAMiDHEAD|WiLDCAT)(?:[\\])])?(?=\\.(?:mkv|mp4|avi|m2ts|ts|wmv|mov|m4v|mpg|mpeg|webm|iso)(?:$|[?#\\s])|$|[?#])))", false),
            new Rule(Group.MEDIA_SOURCE, "gms_remux_2", "REMUX 2", "(?i)(?<![\\s\\S])(?=[\\s\\S]*?(?:(?<![A-Za-z0-9])(?:(?:(?:bd|uhd)[-_. ]?)?remux|bdremux|uhdremux)(?![A-Za-z0-9])))(?=[\\s\\S]*?(?:(?:^|[\\s._-])(?:[\\[(])?(?:ATELiER|NCmt|playBD|SiCFoI|SURFINBIRD|TEPES|PTer)(?:[\\])])?(?=\\.(?:mkv|mp4|avi|m2ts|ts|wmv|mov|m4v|mpg|mpeg|webm|iso)(?:$|[?#\\s])|$|[?#])))", false),
            new Rule(Group.MEDIA_SOURCE, "gms_remux_3", "REMUX 3", "(?i)(?<![\\s\\S])(?=[\\s\\S]*?(?:(?<![A-Za-z0-9])(?:(?:(?:bd|uhd)[-_. ]?)?remux|bdremux|uhdremux)(?![A-Za-z0-9])))(?=[\\s\\S]*?(?:(?:^|[\\s._-])(?:[\\[(])?(?:12GaugeShotgun|decibeL|EPSiLON|HiFi|KRaLiMaRKo|TRiToN)(?:[\\])])?(?=\\.(?:mkv|mp4|avi|m2ts|ts|wmv|mov|m4v|mpg|mpeg|webm|iso)(?:$|[?#\\s])|$|[?#])))", false),
            new Rule(Group.MEDIA_SOURCE, "gms_blu_ray_1", "BLU-RAY 1", "(?i)(?<![\\s\\S])(?=[\\s\\S]*?(?:(?<![A-Za-z0-9])(?:blu[-_. ]?ray|bluray|bdrip|brrip|bdmux|bd(?:720|1080|2160)p?|hd[-_. ]?dvd|hddvd)(?![A-Za-z0-9])))(?=[\\s\\S]*?(?:(?:^|[\\s._-])(?:[\\[(])?(?:CtrlHD|MainFrame|W4NK3R|c0kE|Chotab|CRiSC|D-Z0N3|Dariush|decibeL|EDPH|LolHD|NCmt|PTer|TayTO|VietHD|ZoroSenpai|Geek|DemiHuman|Flugel|LYS1TH3A|NAN0|Moxie|smol|DON|BBQ|BMF|EbP|TDD|TnP|ZQ|FLE|ZR|SoM|sam)(?:[\\])])?(?=\\.(?:mkv|mp4|avi|m2ts|ts|wmv|mov|m4v|mpg|mpeg|webm|iso)(?:$|[?#\\s])|$|[?#])))(?![\\s\\S]*(?:(?<![A-Za-z0-9])(?:(?:(?:bd|uhd)[-_. ]?)?remux|bdremux|uhdremux)(?![A-Za-z0-9])))", false),
            new Rule(Group.MEDIA_SOURCE, "gms_blu_ray_2", "BLU-RAY 2", "(?i)(?<![\\s\\S])(?=[\\s\\S]*?(?:(?<![A-Za-z0-9])(?:blu[-_. ]?ray|bluray|bdrip|brrip|bdmux|bd(?:720|1080|2160)p?|hd[-_. ]?dvd|hddvd)(?![A-Za-z0-9])))(?=[\\s\\S]*?(?:(?:^|[\\s._-])(?:[\\[(])?(?:HQMUX|ATELiER|HiDt|HiSD|SA89|Arg0|FateSucks|hchcsen|hydes|JOHNTiTOR|JySzE|Kulot|LostYears|Meakes|ZeroBuild|Aergia|Arid|koala|Lulu|Vodes|YURI|Orphan)(?:[\\])])?(?=\\.(?:mkv|mp4|avi|m2ts|ts|wmv|mov|m4v|mpg|mpeg|webm|iso)(?:$|[?#\\s])|$|[?#])))(?![\\s\\S]*(?:(?<![A-Za-z0-9])(?:(?:(?:bd|uhd)[-_. ]?)?remux|bdremux|uhdremux)(?![A-Za-z0-9])))", false),
            new Rule(Group.MEDIA_SOURCE, "gms_blu_ray_3", "BLU-RAY 3", "(?i)(?<![\\s\\S])(?=.*(?:^|[\\s._\\-\\[(])(?:blu[.\\- _]?ray|b[dr]rip|bdmux|bd(?:720|1080|2160)|hd[.\\- _]?dvd|dvdrip|ntsc|pal|xvidvd)(?=$|[\\s._\\-\\])]))(?!.*(?:^|[\\s._\\-\\[(])(?:(?:(?:bd|uhd)[.\\- _]?)?remux)(?=$|[\\s._\\-\\])]))(?!.*(?:(?:^\\[(?:CtrlHD|MainFrame|W4NK3R|c0kE|Chotab|CRiSC|D\\-Z0N3|Dariush|decibeL|EDPH|LolHD|NCmt|PTer|TayTO|VietHD|ZoroSenpai|Geek|DemiHuman|Flugel|LYS1TH3A|NAN0|Moxie|smol|HQMUX|ATELiER|HiDt|HiSD|SA89|Arg0|FateSucks|hchcsen|hydes|JOHNTiTOR|JySzE|Kulot|LostYears|Meakes|ZeroBuild|Aergia|Arid|koala|Lulu|Vodes|YURI|Orphan)\\](?=\\s|$|[\\[\\(._-])|^\\((?:CtrlHD|MainFrame|W4NK3R|c0kE|Chotab|CRiSC|D\\-Z0N3|Dariush|decibeL|EDPH|LolHD|NCmt|PTer|TayTO|VietHD|ZoroSenpai|Geek|DemiHuman|Flugel|LYS1TH3A|NAN0|Moxie|smol|HQMUX|ATELiER|HiDt|HiSD|SA89|Arg0|FateSucks|hchcsen|hydes|JOHNTiTOR|JySzE|Kulot|LostYears|Meakes|ZeroBuild|Aergia|Arid|koala|Lulu|Vodes|YURI|Orphan)\\)(?=\\s|$|[\\[\\(._-])|(?:\\[(?:CtrlHD|MainFrame|W4NK3R|c0kE|Chotab|CRiSC|D\\-Z0N3|Dariush|decibeL|EDPH|LolHD|NCmt|PTer|TayTO|VietHD|ZoroSenpai|Geek|DemiHuman|Flugel|LYS1TH3A|NAN0|Moxie|smol|HQMUX|ATELiER|HiDt|HiSD|SA89|Arg0|FateSucks|hchcsen|hydes|JOHNTiTOR|JySzE|Kulot|LostYears|Meakes|ZeroBuild|Aergia|Arid|koala|Lulu|Vodes|YURI|Orphan)\\]|\\((?:CtrlHD|MainFrame|W4NK3R|c0kE|Chotab|CRiSC|D\\-Z0N3|Dariush|decibeL|EDPH|LolHD|NCmt|PTer|TayTO|VietHD|ZoroSenpai|Geek|DemiHuman|Flugel|LYS1TH3A|NAN0|Moxie|smol|HQMUX|ATELiER|HiDt|HiSD|SA89|Arg0|FateSucks|hchcsen|hydes|JOHNTiTOR|JySzE|Kulot|LostYears|Meakes|ZeroBuild|Aergia|Arid|koala|Lulu|Vodes|YURI|Orphan)\\)|(?:(?<=-)|(?<=_)|(?<=\\.)|(?<=\\s-\\s))(?:CtrlHD|MainFrame|W4NK3R|c0kE|Chotab|CRiSC|D\\-Z0N3|Dariush|decibeL|EDPH|LolHD|NCmt|PTer|TayTO|VietHD|ZoroSenpai|Geek|DemiHuman|Flugel|LYS1TH3A|NAN0|Moxie|smol|HQMUX|ATELiER|HiDt|HiSD|SA89|Arg0|FateSucks|hchcsen|hydes|JOHNTiTOR|JySzE|Kulot|LostYears|Meakes|ZeroBuild|Aergia|Arid|koala|Lulu|Vodes|YURI|Orphan)(?:(?:[._-][A-Za-z0-9][A-Za-z0-9+&-]{1,19})){0,3})(?=$|[\\]\\)]|\\.(?:mkv|mp4|avi|m2ts|ts|wmv|mov|m4v|mpg|mpeg|iso|srt|ass|ssa|sub|idx|sup|rar|zip|7z|nfo)\\b|\\s*[,;|]\\s*|\\s{2,}))|(?:^\\[(?:DON|BBQ|BMF|EbP|TDD|TnP|ZQ|FLE|ZR|SoM|sam)\\](?=\\s|$|[\\[\\(._-])|^\\((?:DON|BBQ|BMF|EbP|TDD|TnP|ZQ|FLE|ZR|SoM|sam)\\)(?=\\s|$|[\\[\\(._-])|(?:\\[(?:DON|BBQ|BMF|EbP|TDD|TnP|ZQ|FLE|ZR|SoM|sam)\\]|\\((?:DON|BBQ|BMF|EbP|TDD|TnP|ZQ|FLE|ZR|SoM|sam)\\)|(?:(?<=-)|(?<=_)|(?<=\\.)|(?<=\\s-\\s))(?:DON|BBQ|BMF|EbP|TDD|TnP|ZQ|FLE|ZR|SoM|sam))(?=$|[\\]\\)]|\\.(?:mkv|mp4|avi|m2ts|ts|wmv|mov|m4v|mpg|mpeg|iso|srt|ass|ssa|sub|idx|sup|rar|zip|7z|nfo)\\b|\\s*[,;|]\\s*|\\s{2,}))))(?=.*(?:(?:^\\[(?:BHDStudio|hallowed|HONE|SPHD|WEBDV|HiFi|playHD|LoRD|BBT\\-RMX|ChucksMux|CUNNY|Cunnysseur|Inka\\-Subs|LaCroiX|MTBB|Netaro|Noiy|NTRX|Okay\\-Subs|Sekkon|SubsMix|cappybara|CRUCiBLE|Headpatter|Legion|Mehul|Mysteria|RaiN|RUDY|Serendipity)\\](?=\\s|$|[\\[\\(._-])|^\\((?:BHDStudio|hallowed|HONE|SPHD|WEBDV|HiFi|playHD|LoRD|BBT\\-RMX|ChucksMux|CUNNY|Cunnysseur|Inka\\-Subs|LaCroiX|MTBB|Netaro|Noiy|NTRX|Okay\\-Subs|Sekkon|SubsMix|cappybara|CRUCiBLE|Headpatter|Legion|Mehul|Mysteria|RaiN|RUDY|Serendipity)\\)(?=\\s|$|[\\[\\(._-])|(?:\\[(?:BHDStudio|hallowed|HONE|SPHD|WEBDV|HiFi|playHD|LoRD|BBT\\-RMX|ChucksMux|CUNNY|Cunnysseur|Inka\\-Subs|LaCroiX|MTBB|Netaro|Noiy|NTRX|Okay\\-Subs|Sekkon|SubsMix|cappybara|CRUCiBLE|Headpatter|Legion|Mehul|Mysteria|RaiN|RUDY|Serendipity)\\]|\\((?:BHDStudio|hallowed|HONE|SPHD|WEBDV|HiFi|playHD|LoRD|BBT\\-RMX|ChucksMux|CUNNY|Cunnysseur|Inka\\-Subs|LaCroiX|MTBB|Netaro|Noiy|NTRX|Okay\\-Subs|Sekkon|SubsMix|cappybara|CRUCiBLE|Headpatter|Legion|Mehul|Mysteria|RaiN|RUDY|Serendipity)\\)|(?:(?<=-)|(?<=_)|(?<=\\.)|(?<=\\s-\\s))(?:BHDStudio|hallowed|HONE|SPHD|WEBDV|HiFi|playHD|LoRD|BBT\\-RMX|ChucksMux|CUNNY|Cunnysseur|Inka\\-Subs|LaCroiX|MTBB|Netaro|Noiy|NTRX|Okay\\-Subs|Sekkon|SubsMix|cappybara|CRUCiBLE|Headpatter|Legion|Mehul|Mysteria|RaiN|RUDY|Serendipity)(?:(?:[._-][A-Za-z0-9][A-Za-z0-9+&-]{1,19})){0,3})(?=$|[\\]\\)]|\\.(?:mkv|mp4|avi|m2ts|ts|wmv|mov|m4v|mpg|mpeg|iso|srt|ass|ssa|sub|idx|sup|rar|zip|7z|nfo)\\b|\\s*[,;|]\\s*|\\s{2,}))|(?:^\\[(?:npz|P9|RMX|ARC|Doc|fig|sgt|uba)\\](?=\\s|$|[\\[\\(._-])|^\\((?:npz|P9|RMX|ARC|Doc|fig|sgt|uba)\\)(?=\\s|$|[\\[\\(._-])|(?:\\[(?:npz|P9|RMX|ARC|Doc|fig|sgt|uba)\\]|\\((?:npz|P9|RMX|ARC|Doc|fig|sgt|uba)\\)|(?:(?<=-)|(?<=_)|(?<=\\.)|(?<=\\s-\\s))(?:npz|P9|RMX|ARC|Doc|fig|sgt|uba))(?=$|[\\]\\)]|\\.(?:mkv|mp4|avi|m2ts|ts|wmv|mov|m4v|mpg|mpeg|iso|srt|ass|ssa|sub|idx|sup|rar|zip|7z|nfo)\\b|\\s*[,;|]\\s*|\\s{2,}))))", false),
            new Rule(Group.MEDIA_SOURCE, "gms_blu_ray_4", "BLU-RAY 4", "(?i)(?<![\\s\\S])(?=[\\s\\S]*?(?:(?<![A-Za-z0-9])(?:blu[-_. ]?ray|bluray|bdrip|brrip|bdmux|bd(?:720|1080|2160)p?|hd[-_. ]?dvd|hddvd)(?![A-Za-z0-9])))(?=[\\s\\S]*?(?:(?:^|[\\s._-])(?:[\\[(])?(?:Afro|Chimera|derp|Foxtrot|Kawatare|Metal|Pizza|Smoke|Vanilla|VULCAN|ABdex|aRMX|BiRJU|grimf|Kaleido-subs|Kametsu|LazyRemux|neko-kBaraka|pog42|Quetzal|Reza|Shimatta|Spirale|Virtuality|DIY|EXP|BKC|CBT|IK|KH|MK|OZR|SCY|UDF|UQW)(?:[\\])])?(?=\\.(?:mkv|mp4|avi|m2ts|ts|wmv|mov|m4v|mpg|mpeg|webm|iso)(?:$|[?#\\s])|$|[?#])))(?![\\s\\S]*(?:(?<![A-Za-z0-9])(?:(?:(?:bd|uhd)[-_. ]?)?remux|bdremux|uhdremux)(?![A-Za-z0-9])))", false),
            new Rule(Group.MEDIA_SOURCE, "gms_blu_ray_5", "BLU-RAY 5", "(?i)(?<![\\s\\S])(?=[\\s\\S]*?(?:(?<![A-Za-z0-9])(?:blu[-_. ]?ray|bluray|bdrip|brrip|bdmux|bd(?:720|1080|2160)p?|hd[-_. ]?dvd|hddvd)(?![A-Za-z0-9])))(?=[\\s\\S]*?(?:(?:^|[\\s._-])(?:[\\[(])?(?:Animorphs|AOmundson|Baws|McBalls|B00BA|Cait-Sidhe|deanzel|eldon|Freehold|Hark0N|Holomux|mottoj|NTRM|TTGA|UltraRemux|WBDP|Beatrice|Drag|Judgment|Thighs|Yuki|ASC|CsS|CTR|D4C|GHS|MC|NH|o7|QM|WSE)(?:[\\])])?(?=\\.(?:mkv|mp4|avi|m2ts|ts|wmv|mov|m4v|mpg|mpeg|webm|iso)(?:$|[?#\\s])|$|[?#])))(?![\\s\\S]*(?:(?<![A-Za-z0-9])(?:(?:(?:bd|uhd)[-_. ]?)?remux|bdremux|uhdremux)(?![A-Za-z0-9])))", false),
            new Rule(Group.MEDIA_SOURCE, "gms_blu_ray_6", "BLU-RAY 6", "(?i)(?<![\\s\\S])(?=[\\s\\S]*?(?:(?<![A-Za-z0-9])(?:blu[-_. ]?ray|bluray|bdrip|brrip|bdmux|bd(?:720|1080|2160)p?|hd[-_. ]?dvd|hddvd)(?![A-Za-z0-9])))(?=[\\s\\S]*?(?:(?:^|[\\s._-])(?:[\\[(])?(?:Tsundere|YURASUKA|Bunny-Apocalypse|Datte13|GetItTwisted|iKaos|karios|Pookie|RASETSU|Starbez|Yoghurt)(?:[\\])])?(?=\\.(?:mkv|mp4|avi|m2ts|ts|wmv|mov|m4v|mpg|mpeg|webm|iso)(?:$|[?#\\s])|$|[?#])))(?![\\s\\S]*(?:(?<![A-Za-z0-9])(?:(?:(?:bd|uhd)[-_. ]?)?remux|bdremux|uhdremux)(?![A-Za-z0-9])))", false),
            new Rule(Group.MEDIA_SOURCE, "gms_blu_ray_7", "BLU-RAY 7", "(?i)(?<![\\s\\S])(?=[\\s\\S]*?(?:(?<![A-Za-z0-9])(?:blu[-_. ]?ray|bluray|bdrip|brrip|bdmux|bd(?:720|1080|2160)p?|hd[-_. ]?dvd|hddvd)(?![A-Za-z0-9])))(?=[\\s\\S]*?(?:(?:^|[\\s._-])(?:[\\[(])?(?:Almighty|Asakura|Bolshevik|Chihiro|Crow|Dekinai|Senjou|Vivid|9volt|Asenshi|BlurayDesuYo|Brrrrrrr|Commie|Dragon-Releases|DragsterPS|Exiled-Destiny|Final8|Geonope|iAHD|inid4c|kuchikirukia|REVO|SCP-2223|THORA|AC|Dae|E-D|FFF|GJM|LCE|NTW|orz|RAI|SEV)(?:[\\])])?(?=\\.(?:mkv|mp4|avi|m2ts|ts|wmv|mov|m4v|mpg|mpeg|webm|iso)(?:$|[?#\\s])|$|[?#])))(?![\\s\\S]*(?:(?<![A-Za-z0-9])(?:(?:(?:bd|uhd)[-_. ]?)?remux|bdremux|uhdremux)(?![A-Za-z0-9])))", false),
            new Rule(Group.MEDIA_SOURCE, "gms_blu_ray_8", "BLU-RAY 8", "(?i)(?<![\\s\\S])(?=[\\s\\S]*?(?:(?<![A-Za-z0-9])(?:blu[-_. ]?ray|bluray|bdrip|brrip|bdmux|bd(?:720|1080|2160)p?|hd[-_. ]?dvd|hddvd)(?![A-Za-z0-9])))(?=[\\s\\S]*?(?:(?:^|[\\s._-])(?:[\\[(])?(?:EDGE|EMBER|GHOST|naiyas|Prof|Judas|AkihitoSubs|Arukoru)(?:[\\])])?(?=\\.(?:mkv|mp4|avi|m2ts|ts|wmv|mov|m4v|mpg|mpeg|webm|iso)(?:$|[?#\\s])|$|[?#])))(?![\\s\\S]*(?:(?<![A-Za-z0-9])(?:(?:(?:bd|uhd)[-_. ]?)?remux|bdremux|uhdremux)(?![A-Za-z0-9])))", false),
            new Rule(Group.MEDIA_SOURCE, "gms_web_1", "WEB 1", "(?i)(?<![\\s\\S])(?=[\\s\\S]*?(?:(?<![A-Za-z0-9])(?:web(?:[-_. ]?dl(?:mux)?|[-_. ]?rip|mux)?|webdl|webrip|webmux)(?![A-Za-z0-9])))(?=[\\s\\S]*?(?:(?:^|[\\s._-])(?:[\\[(])?(?:ABBIE|AJP69|CMRG|CRFW|CRUD|HONE|NOSiViD|PAXA|PEXA|RAWR|TheFarm|XEPA|ZoroSenpai|CasStudio|CtrlHD|monkee|ViSUM|APEX|FLUX|KiNGS|TOMMY|Arg0|Baws|LostYears|LYS1TH3A|McBalls|Setsugen|Z4ST1N|ZeroBuild|Arid|smol|Vodes|NTb|NTG|SiC|QOQ|RTN|T6D|FLE|SCY|SoM|sam|Kitsune)(?:[\\])])?(?=\\.(?:mkv|mp4|avi|m2ts|ts|wmv|mov|m4v|mpg|mpeg|webm|iso)(?:$|[?#\\s])|$|[?#])))(?![\\s\\S]*(?:(?:(?<![A-Za-z0-9])(?:(?:(?:bd|uhd)[-_. ]?)?remux|bdremux|uhdremux)(?![A-Za-z0-9])|(?<![A-Za-z0-9])(?:blu[-_. ]?ray|bluray|bdrip|brrip|bdmux|bd(?:720|1080|2160)p?|hd[-_. ]?dvd|hddvd)(?![A-Za-z0-9]))))", false),
            new Rule(Group.MEDIA_SOURCE, "gms_web_2", "WEB 2", "(?i)(?<![\\s\\S])(?=[\\s\\S]*?(?:(?<![A-Za-z0-9])(?:web(?:[-_. ]?dl(?:mux)?|[-_. ]?rip|mux)?|webdl|webrip|webmux)(?![A-Za-z0-9])))(?=[\\s\\S]*?(?:(?:^|[\\s._-])(?:[\\[(])?(?:BLUTONiUM|BYNDR|GNOME|TEPES|dB|MiU|SbR|BTW|CiT|FC|iJP|iKA|KHN|NYH|END|Dae|Gao|NPMS|ROCCaT|SiGMA)(?:[\\])])?(?=\\.(?:mkv|mp4|avi|m2ts|ts|wmv|mov|m4v|mpg|mpeg|webm|iso)(?:$|[?#\\s])|$|[?#])))(?![\\s\\S]*(?:(?:(?<![A-Za-z0-9])(?:(?:(?:bd|uhd)[-_. ]?)?remux|bdremux|uhdremux)(?![A-Za-z0-9])|(?<![A-Za-z0-9])(?:blu[-_. ]?ray|bluray|bdrip|brrip|bdmux|bd(?:720|1080|2160)p?|hd[-_. ]?dvd|hddvd)(?![A-Za-z0-9]))))", false),
            new Rule(Group.MEDIA_SOURCE, "gms_web_3", "WEB 3", "(?i)(?<![\\s\\S])(?=[\\s\\S]*?(?:(?<![A-Za-z0-9])(?:web(?:[-_. ]?dl(?:mux)?|[-_. ]?rip|mux)?|webdl|webrip|webmux)(?![A-Za-z0-9])))(?=[\\s\\S]*?(?:(?:^|[\\s._-])(?:[\\[(])?(?:BLOOM|Dooky|GNOMiSSiON|HHWEB|NINJACENTRAL|SLiGNOME|SwAgLaNdEr|DRACULA|ViSiON|AnoZu|T4H|ZR)(?:[\\])])?(?=\\.(?:mkv|mp4|avi|m2ts|ts|wmv|mov|m4v|mpg|mpeg|webm|iso)(?:$|[?#\\s])|$|[?#])))(?![\\s\\S]*(?:(?:(?<![A-Za-z0-9])(?:(?:(?:bd|uhd)[-_. ]?)?remux|bdremux|uhdremux)(?![A-Za-z0-9])|(?<![A-Za-z0-9])(?:blu[-_. ]?ray|bluray|bdrip|brrip|bdmux|bd(?:720|1080|2160)p?|hd[-_. ]?dvd|hddvd)(?![A-Za-z0-9]))))", false),
            new Rule(Group.MEDIA_SOURCE, "gms_web_4", "WEB 4", "(?i)(?<![\\s\\S])(?=[\\s\\S]*?(?:(?<![A-Za-z0-9])(?:web(?:[-_. ]?dl(?:mux)?|[-_. ]?rip|mux)?|webdl|webrip|webmux)(?![A-Za-z0-9])))(?=[\\s\\S]*?(?:(?:^|[\\s._-])(?:[\\[(])?(?:Erai-raws|ToonsHub|VARYG)(?:[\\])])?(?=\\.(?:mkv|mp4|avi|m2ts|ts|wmv|mov|m4v|mpg|mpeg|webm|iso)(?:$|[?#\\s])|$|[?#])))(?![\\s\\S]*(?:(?:(?<![A-Za-z0-9])(?:(?:(?:bd|uhd)[-_. ]?)?remux|bdremux|uhdremux)(?![A-Za-z0-9])|(?<![A-Za-z0-9])(?:blu[-_. ]?ray|bluray|bdrip|brrip|bdmux|bd(?:720|1080|2160)p?|hd[-_. ]?dvd|hddvd)(?![A-Za-z0-9]))))", false),
            new Rule(Group.MEDIA_SOURCE, "gms_web_5", "WEB 5", "(?i)(?<![\\s\\S])(?=[\\s\\S]*?(?:(?<![A-Za-z0-9])(?:web(?:[-_. ]?dl(?:mux)?|[-_. ]?rip|mux)?|webdl|webrip|webmux)(?![A-Za-z0-9])))(?=[\\s\\S]*?(?:(?:^|[\\s._-])(?:[\\[(])?(?:BlueLobster|HorribleRips|HorribleSubs|KAN3D2M|KiyoshiStar|NanDesuKa|SobsPlease|Some-Stuffs|SubsPlease|URANIME|ZigZag|ZigZab)(?:[\\])])?(?=\\.(?:mkv|mp4|avi|m2ts|ts|wmv|mov|m4v|mpg|mpeg|webm|iso)(?:$|[?#\\s])|$|[?#])))(?![\\s\\S]*(?:(?:(?<![A-Za-z0-9])(?:(?:(?:bd|uhd)[-_. ]?)?remux|bdremux|uhdremux)(?![A-Za-z0-9])|(?<![A-Za-z0-9])(?:blu[-_. ]?ray|bluray|bdrip|brrip|bdmux|bd(?:720|1080|2160)p?|hd[-_. ]?dvd|hddvd)(?![A-Za-z0-9]))))", false),
            new Rule(Group.MEDIA_SOURCE, "gms_web_6", "WEB 6", "(?i)(?<![\\s\\S])(?=[\\s\\S]*?(?:(?<![A-Za-z0-9])(?:web(?:[-_. ]?dl(?:mux)?|[-_. ]?rip|mux)?|webdl|webrip|webmux)(?![A-Za-z0-9])))(?=[\\s\\S]*?(?:(?:^|[\\s._-])(?:[\\[(])?(?:9volt|Asenshi|Commie|DameDesuYo|Kaleido|KawaSubs|Chihiro|Doki|Kantai|Tsundere)(?:[\\])])?(?=\\.(?:mkv|mp4|avi|m2ts|ts|wmv|mov|m4v|mpg|mpeg|webm|iso)(?:$|[?#\\s])|$|[?#])))(?![\\s\\S]*(?:(?:(?<![A-Za-z0-9])(?:(?:(?:bd|uhd)[-_. ]?)?remux|bdremux|uhdremux)(?![A-Za-z0-9])|(?<![A-Za-z0-9])(?:blu[-_. ]?ray|bluray|bdrip|brrip|bdmux|bd(?:720|1080|2160)p?|hd[-_. ]?dvd|hddvd)(?![A-Za-z0-9]))))", false),
            new Rule(Group.MEDIA_SOURCE, "gms_iso", "ISO", "(?i)(?:\\biso\\b|原盘镜像|镜像原盘)", false),
            new Rule(Group.MEDIA_SOURCE, "gms_high_bitrate", "HIGH BITRATE", "(?i)(?:高码率|高码|high[\\s._-]*bitrate)", false),
            new Rule(Group.RESOLUTION, "gr_8k", "8K", "(?i)\\b(?:8k|4320[pi]?)\\b", true),
            new Rule(Group.RESOLUTION, "gr_4k", "4K", "(?i)^(?=.*(?:2160[pi]?|4k|uhd|ultra[\\s._-]?hd))(?!.*(?:1080[pi]?|720[pi]?))", true),
            new Rule(Group.RESOLUTION, "gr_1440p", "1440p", "(?i)^(?=.*(?:1440[pi]?|2560x1440|qhd))(?!.*(?:2160[pi]?|4k|uhd|ultra[\\s._-]?hd))", true),
            new Rule(Group.RESOLUTION, "gr_1080p", "1080p", "(?i)\\b1080[pi]?\\b", true),
            new Rule(Group.RESOLUTION, "gr_720p", "720p", "(?i)\\b720[pi]?\\b", true),
            new Rule(Group.RESOLUTION, "gr_576p", "576p", "(?i)\\b576[pi]?\\b", true),
            new Rule(Group.RESOLUTION, "gr_480p", "480p", "(?i)\\b480[pi]?\\b", true),
            new Rule(Group.RESOLUTION, "gr_360p", "360p", "(?i)\\b360[pi]?\\b", true),
            new Rule(Group.RESOLUTION, "gr_240p", "240p", "(?i)\\b240[pi]?\\b", true),
            new Rule(Group.RESOLUTION, "gr_144p", "144p", "(?i)\\b144[pi]?\\b", true),
            new Rule(Group.QUALITY, "gq_remux", "Remux", "(?i)\\bremux\\b", false),
            new Rule(Group.QUALITY, "gq_bluray", "BluRay", "(?i)^(?=.*(?:bluray|blu-ray))(?!.*remux)", false),
            new Rule(Group.QUALITY, "gq_web_dl", "WEB-DL", "(?i)\\b(?:web[-_. ]?dl|webdl)\\b", false),
            new Rule(Group.QUALITY, "gq_webrip", "WEBRip", "(?i)\\bweb[-_. ]?rip\\b", false),
            new Rule(Group.QUALITY, "gq_hdrip", "HDRip", "(?i)\\bhd[\\s._-]?rip\\b", false),
            new Rule(Group.QUALITY, "gq_hc_hdrip", "HC HDRip", "(?i)(?:^(?=[\\s\\S]*\u2063\u200c\u200c\u200d\u200c\u200c\u200d\u200d\u200d\u2064)(?![\\s\\S]*(?:\u2063\u200c\u200c\u200d\u200c\u200c\u200c\u200d\u200c\u2064|\u2063\u200c\u200c\u200d\u200c\u200c\u200c\u200d\u200d\u2064|\u2063\u200c\u200c\u200d\u200c\u200c\u200d\u200c\u200c\u2064|\u2063\u200c\u200c\u200d\u200c\u200c\u200d\u200c\u200d\u2064))[\\s\\S]*$|^(?![\\s\\S]*(?:\u2063\u200c\u200c\u200d\u200c\u200c\u200c\u200d\u200c\u2064|\u2063\u200c\u200c\u200d\u200c\u200c\u200c\u200d\u200d\u2064|\u2063\u200c\u200c\u200d\u200c\u200c\u200d\u200c\u200c\u2064|\u2063\u200c\u200c\u200d\u200c\u200c\u200d\u200c\u200d\u2064|\u2063\u200c\u200c\u200d\u200c\u200c\u200d\u200d\u200c\u2064|\u2063\u200c\u200c\u200d\u200c\u200c\u200d\u200d\u200d\u2064|\u2063\u200c\u200c\u200d\u200c\u200d\u200c\u200c\u200c\u2064|\u2063\u200c\u200c\u200d\u200c\u200d\u200c\u200c\u200d\u2064|\u2063\u200c\u200c\u200d\u200c\u200d\u200c\u200d\u200c\u2064|\u2063\u200c\u200c\u200d\u200c\u200d\u200c\u200d\u200d\u2064|\u2063\u200c\u200c\u200d\u200c\u200d\u200d\u200c\u200c\u2064|\u2063\u200c\u200c\u200d\u200c\u200d\u200d\u200c\u200d\u2064))(?![\\s\\S]*(?:(?:(?<![a-z0-9])(?:(?:bd|uhd)?remux)(?=$|[^a-z0-9])|(?<![a-z0-9])(?:bluray|blu[-_. ]?ray|bdrip|brrip|bdmux|bd(?:720|1080|2160)?p?)(?=$|[^a-z0-9])|(?<![a-z0-9])(?:web[-_. ]?dl(?:mux)?|webdl)(?=$|[^a-z0-9])|(?<![a-z0-9])(?:web[-_. ]?rip|webrip)(?=$|[^a-z0-9]))))(?:[\\s\\S]*(?<![a-z0-9])hc[-_. ]?hd[-_. ]?rip(?=$|[^a-z0-9])|(?=[\\s\\S]*(?:(?<![a-z0-9])(?:hardcoded|hardsub|korsub)(?=$|[^a-z0-9])))[\\s\\S]*(?<![a-z0-9])hd[-_. ]?rip(?=$|[^a-z0-9])))", false),
            new Rule(Group.QUALITY, "gq_dvdrip", "DVDRip", "(?i)\\b(?:dvd[-_. ]?rip|dvdrip)\\b", false),
            new Rule(Group.QUALITY, "gq_hdtv", "HDTV", "(?i)\\bhdtv\\b", false),
            new Rule(Group.QUALITY, "gq_scr", "SCR", "(?i)\\b(?:screener|dvd[\\s._-]?scr|scr)\\b", false),
            new Rule(Group.QUALITY, "gq_tc", "TC", "(?i)\\b(?:telecine|hd[\\s._-]?tc)\\b", false),
            new Rule(Group.QUALITY, "gq_ts", "TS", "(?i)\\b(?:telesync|hd[\\s._-]?ts)\\b", false),
            new Rule(Group.QUALITY, "gq_cam", "CAM", "(?i)\\b(?:cam[\\s._-]?rip|hd[\\s._-]?cam|hdcam)\\b", false),
            new Rule(Group.IMAX, "imax_imax_enhanced", "IMAX Enhanced", "(?i)\\bimax[\\s._-]?enhanced\\b", false),
            new Rule(Group.IMAX, "imax_imax", "IMAX", "(?i)^(?=.*\\bimax\\b)(?!.*\\bimax[\\s._-]?enhanced\\b)", false),
            new Rule(Group.DYNAMIC_RANGE, "gv_dolby_vision_hdr10_plus", "DV \u00b7 HDR10+", "(?i)^(?=.*\\b(?:dv|dovi|dolby[\\s._-]?vision)\\b)(?=.*hdr[\\s._-]?10[\\s._-]?(?:\\+|(?:plus|p)\\b))", true),
            new Rule(Group.DYNAMIC_RANGE, "gv_dolby_vision_hdr10", "DV \u00b7 HDR10", "(?i)^(?=.*\\b(?:dv|dovi|dolby[\\s._-]?vision)\\b)(?=.*hdr[\\s._-]?10(?![\\s._-]?bits?\\b))(?!.*hdr[\\s._-]?10[\\s._-]?(?:\\+|(?:plus|p)\\b))", true),
            new Rule(Group.DYNAMIC_RANGE, "gv_dolby_vision_hdr", "DV \u00b7 HDR", "(?i)^(?=.*\\b(?:dv|dovi|dolby[\\s._-]?vision)\\b)(?=.*(?:\\bhdr\\b|\\bhlg\\b))(?!.*hdr[\\s._-]?10[\\s._-]?(?:\\+|(?:plus|p)\\b))(?!.*hdr[\\s._-]?10(?![\\s._-]?bits?\\b))", true),
            new Rule(Group.DYNAMIC_RANGE, "gv_dolby_vision", "DV", "(?i)^(?=.*\\b(?:dv|dovi|dolby[\\s._-]?vision)\\b)(?!.*hdr[\\s._-]?10[\\s._-]?(?:\\+|(?:plus|p)\\b))(?!.*hdr[\\s._-]?10(?![\\s._-]?bits?\\b))(?!.*\\bhdr\\b)(?!.*\\bhlg\\b)", true),
            new Rule(Group.DYNAMIC_RANGE, "gv_hdr10_plus", "HDR10+", "(?i)^(?=.*hdr[\\s._-]?10[\\s._-]?(?:\\+|(?:plus|p)\\b))(?!.*\\b(?:dv|dovi|dolby[\\s._-]?vision)\\b)", true),
            new Rule(Group.DYNAMIC_RANGE, "gv_hdr10", "HDR10", "(?i)^(?=.*hdr[\\s._-]?10(?![\\s._-]?bits?\\b))(?!.*hdr[\\s._-]?10[\\s._-]?(?:\\+|(?:plus|p)\\b))(?!.*\\b(?:dv|dovi|dolby[\\s._-]?vision)\\b)", true),
            new Rule(Group.DYNAMIC_RANGE, "gv_hdr", "HDR", "(?i)^(?=.*\\bhdr\\b)(?!.*hdr[\\s._-]?10[\\s._-]?(?:\\+|(?:plus|p)\\b))(?!.*hdr[\\s._-]?10(?![\\s._-]?bits?\\b))(?!.*\\b(?:dv|dovi|dolby[\\s._-]?vision)\\b)", true),
            new Rule(Group.DYNAMIC_RANGE, "gv_hlg", "HLG", "(?i)^(?=.*\\bhlg\\b)(?!.*hdr[\\s._-]?10[\\s._-]?(?:\\+|(?:plus|p)\\b))(?!.*hdr[\\s._-]?10(?![\\s._-]?bits?\\b))(?!.*\\bhdr\\b)(?!.*\\b(?:dv|dovi|dolby[\\s._-]?vision)\\b)", true),
            new Rule(Group.DYNAMIC_RANGE, "gv_10bit", "10bit", "(?i)^(?=.*(?:\\b10[\\s._-]?bits?\\b|\\bhi10p?\\b))(?!.*hdr[\\s._-]?10[\\s._-]?(?:\\+|(?:plus|p)\\b))(?!.*hdr[\\s._-]?10(?![\\s._-]?bits?\\b))(?!.*\\bhdr\\b)(?!.*\\bhlg\\b)(?!.*\\b(?:dv|dovi|dolby[\\s._-]?vision)\\b)", true),
            new Rule(Group.DYNAMIC_RANGE, "gv_sdr", "SDR", "(?i)^(?=.*\\bsdr\\b)(?!.*hdr[\\s._-]?10[\\s._-]?(?:\\+|(?:plus|p)\\b))(?!.*hdr[\\s._-]?10(?![\\s._-]?bits?\\b))(?!.*\\bhdr\\b)(?!.*\\bhlg\\b)(?!.*(?:\\b10[\\s._-]?bits?\\b|\\bhi10p?\\b))(?!.*\\b(?:dv|dovi|dolby[\\s._-]?vision)\\b)", true),
            new Rule(Group.DYNAMIC_RANGE, "gv_ai", "AI", "(?i)^(?=.*\\b(?:ai[\\s._-]?(?:upscal\\w*|enhanc\\w*)|upscaled?)\\b)(?!.*hdr[\\s._-]?10[\\s._-]?(?:\\+|(?:plus|p)\\b))(?!.*hdr[\\s._-]?10(?![\\s._-]?bits?\\b))(?!.*\\bhdr\\b)(?!.*\\bhlg\\b)(?!.*(?:\\b10[\\s._-]?bits?\\b|\\bhi10p?\\b))(?!.*\\bsdr\\b)(?!.*\\b(?:dv|dovi|dolby[\\s._-]?vision)\\b)", true),
            new Rule(Group.DYNAMIC_RANGE, "gv_12bit", "12bit", "(?i)\\b12[\\s._-]?bits?\\b", true),
            new Rule(Group.DYNAMIC_RANGE, "gv_8bit", "8bit", "(?i)\\b8[\\s._-]?bits?\\b", true),
            new Rule(Group.VIDEO_TECH, "gvf_hdrvivid", "HDR VIVID", "(?i)(?:hdr[\\s._-]*vivid|hdrvivid|菁彩hdr|菁彩视听)", false),
            new Rule(Group.VIDEO_TECH, "gvf_3d", "3D", "(?i)\\b3d\\b", false),
            new Rule(Group.AUDIO, "ga_dolby_atmos_truehd", "ATMOS TRUEHD", "(?i)^(?=.*\\batmos\\b)(?=.*\\btrue[\\s._-]?hd\\b)", true),
            new Rule(Group.AUDIO, "ga_dts_x_hd_ma", "DTS:X HD MA", "(?i)^(?=.*\\bdts[-_.: ]?x\\b)(?=.*\\bdts[-_. ]?(?:hd[-_. ]?)?ma\\b)", true),
            new Rule(Group.AUDIO, "ga_dolby_atmos_digital_plus", "ATMOS DIGITAL+", "(?i)^(?=.*\\batmos\\b)(?=.*(?:\\bddp|\\bdd\\+|\\beac-?3|\\be-?ac-?3))(?!.*\\btrue[\\s._-]?hd\\b)", true),
            new Rule(Group.AUDIO, "ga_dts_x_hd", "DTS:X HD", "(?i)^(?=.*\\bdts[-_.: ]?x\\b)(?=.*\\bdts[-_. ]?hd\\b)(?!.*\\bdts[-_. ]?(?:hd[-_. ]?)?ma\\b)", true),
            new Rule(Group.AUDIO, "ga_dolby_atmos", "Atmos", "(?i)^(?=.*\\batmos\\b)(?!.*\\btrue[\\s._-]?hd\\b)(?!.*(?:\\bddp|\\bdd\\+|\\beac-?3|\\be-?ac-?3))", true),
            new Rule(Group.AUDIO, "ga_dts_x", "DTS:X", "(?i)^(?=.*\\bdts[-_.: ]?x\\b)(?!.*\\bdts[-_. ]?(?:hd[-_. ]?)?ma\\b)(?!.*\\bdts[-_. ]?hd\\b)", true),
            new Rule(Group.AUDIO, "ga_dolby_truehd", "TrueHD", "(?i)^(?=.*\\btrue[\\s._-]?hd\\b)(?!.*\\batmos\\b)", true),
            new Rule(Group.AUDIO, "ga_dts_hd_ma", "DTS-HD MA", "(?i)^(?=.*\\bdts[-_. ]?(?:hd[-_. ]?)?ma\\b)(?!.*\\bdts[-_.: ]?x\\b)", true),
            new Rule(Group.AUDIO, "ga_flac", "FLAC", "(?i)\\bflac\\b", true),
            new Rule(Group.AUDIO, "ga_dts_hd", "DTS-HD", "(?i)^(?=.*\\bdts[-_. ]?hd\\b)(?!.*\\bdts[-_. ]?(?:hd[-_. ]?)?ma\\b)(?!.*\\bdts[-_.: ]?x\\b)", true),
            new Rule(Group.AUDIO, "ga_dolby_digital_plus", "DD+", "(?i)^(?=.*(?:\\bddp|\\bdd\\+|\\beac-?3|\\be-?ac-?3))(?!.*\\batmos\\b)(?!.*\\btrue[\\s._-]?hd\\b)", true),
            new Rule(Group.AUDIO, "ga_dts_es", "DTS-ES", "(?i)^(?=.*\\bdts[-_. ]?es\\b)(?!.*\\bdts[-_. ]?hd\\b)(?!.*\\bdts[-_. ]?(?:hd[-_. ]?)?ma\\b)(?!.*\\bdts[-_.: ]?x\\b)", true),
            new Rule(Group.AUDIO, "ga_dts", "DTS", "(?i)^(?=.*\\bdts\\b)(?!.*\\bdts[-_. ]?es\\b)(?!.*\\bdts[-_. ]?hd\\b)(?!.*\\bdts[-_. ]?(?:hd[-_. ]?)?ma\\b)(?!.*\\bdts[-_.: ]?x\\b)", true),
            new Rule(Group.AUDIO, "ga_opus", "OPUS", "(?i)^(?=.*\\bopus\\b)(?!.*(?:\\batmos\\b|\\btrue[\\s._-]?hd\\b|(?:\\bddp|\\bdd\\+|\\beac-?3|\\be-?ac-?3)|\\b(?:dd[25][. ][01]|ac-?3)\\b|\\bdts[-_.: ]?x\\b|\\bdts[-_. ]?(?:hd[-_. ]?)?ma\\b|\\bdts[-_. ]?hd\\b|\\bdts[-_. ]?es\\b|\\bdts\\b|\\bflac\\b))", true),
            new Rule(Group.AUDIO, "ga_dolby_digital", "DD", "(?i)^(?=.*\\b(?:dd[25][. ][01]|ac-?3)\\b)(?!.*(?:\\bddp|\\bdd\\+|\\beac-?3|\\be-?ac-?3))(?!.*\\batmos\\b)(?!.*\\btrue[\\s._-]?hd\\b)", true),
            new Rule(Group.AUDIO, "ga_aac", "AAC", "(?i)^(?=.*\\b(?:aac|qaac)\\b)(?!.*(?:\\batmos\\b|\\btrue[\\s._-]?hd\\b|(?:\\bddp|\\bdd\\+|\\beac-?3|\\be-?ac-?3)|\\b(?:dd[25][. ][01]|ac-?3)\\b|\\bdts[-_.: ]?x\\b|\\bdts[-_. ]?(?:hd[-_. ]?)?ma\\b|\\bdts[-_. ]?hd\\b|\\bdts[-_. ]?es\\b|\\bdts\\b|\\bflac\\b|\\bopus\\b))", true),
            new Rule(Group.AUDIO, "ga_lpcm", "LPCM", "(?i)\\b(?:lpcm|pcm)\\b", true),
            new Rule(Group.AUDIO, "ga_lossless", "LOSSLESS", "(?i)(?:无损|lossless|\\balac\\b|\\bape\\b|\\bwav\\b)", true),
            new Rule(Group.CHANNELS, "gc_7_1_4", "7.1.4", "(?i)\\b7[.\\-]1[.\\-]4\\b", true),
            new Rule(Group.CHANNELS, "gc_5_1_4", "5.1.4", "(?i)\\b5[.\\-]1[.\\-]4\\b", true),
            new Rule(Group.CHANNELS, "gc_5_1_2", "5.1.2", "(?i)\\b5[.\\-]1[.\\-]2\\b", true),
            new Rule(Group.CHANNELS, "gc_7_1", "7.1", "[^0-9][7-8][. ][01](?![0-9])", true),
            new Rule(Group.CHANNELS, "gc_6_1", "6.1", "^(?=.*[^0-9]6[. ][01](?![0-9]))(?!.*[^0-9][7-8][. ][01](?![0-9]))", true),
            new Rule(Group.CHANNELS, "gc_5_1", "5.1", "^(?=.*[^0-9]5[. ][01](?![0-9]))(?!.*[^0-9][7-8][. ][01](?![0-9]))", true),
            new Rule(Group.CHANNELS, "gc_2_0", "2.0", "^(?=.*[^0-9]2[. ]0(?![0-9]))(?!.*[^0-9][5-8][. ][01](?![0-9]))", true),
            new Rule(Group.FRAME_RATE, "gfr_hfr", "HFR", "(?i)(?:高帧率|\\b(?:60|120)fps\\b)", false),
            new Rule(Group.VIDEO_CODEC, "ge_av1", "AV1", "(?i)\\bav1\\b", true),
            new Rule(Group.VIDEO_CODEC, "ge_hevc", "HEVC", "(?i)\\b(?:x265|h[._-]?265|hevc)\\b", true),
            new Rule(Group.VIDEO_CODEC, "ge_avc", "AVC", "(?i)\\b(?:x264|h[._-]?264|avc)\\b", true),
            new Rule(Group.VIDEO_CODEC, "ge_xvid", "XviD", "(?i)\\bxvid\\b", true),
            new Rule(Group.VIDEO_CODEC, "ge_divx", "DivX", "(?i)\\bdivx\\b", true),
            new Rule(Group.VIDEO_CODEC, "ge_vvc", "VVC", "(?i)\\b(?:vvc|h[\\s._-]?266|x266)\\b", true),
            new Rule(Group.STREAMING, "gs_peacock", "PEACOCK", "(?i)\\b(?:pcok|peacock)\\b", false),
            new Rule(Group.STREAMING, "gs_netflix", "NETFLIX", "(?i)\\b(?:nflx|netflix)\\b", false),
            new Rule(Group.STREAMING, "gs_prime_video", "PRIME VIDEO", "(?i)\\b(?:amzn|amazon|primevideo|prime[\\s._-]?video)\\b", false),
            new Rule(Group.STREAMING, "gs_apple_tv_plus", "APPLE TV+", "(?i)\\b(?:atvp|appletv|apple[\\s._-]?tv)\\b", false),
            new Rule(Group.STREAMING, "gs_disney_plus", "DISNEY+", "(?i)(?:\\b(?:dsnp|dsny)\\b|\\bdisney\\+)", false),
            new Rule(Group.STREAMING, "gs_hbo_max", "HBO MAX", "(?i)\\b(?:hmax|hbomax|hbo[\\s._-]?max)\\b", false),
            new Rule(Group.STREAMING, "gs_hulu", "HULU", "(?i)\\bhulu\\b", false),
            new Rule(Group.STREAMING, "gs_paramount_plus", "PARAMOUNT+", "(?i)\\b(?:pmtp|pamp|paramount)\\b", false),
            new Rule(Group.STREAMING, "gs_crunchyroll", "CRUNCHYROLL", "(?i)\\b(?:cr|crunchyroll)\\b", false),
    };

    private static volatile Pattern[] compiled;

    private MediaBadgeCatalog() {
    }

    public static Rule[] rules() {
        return RULES;
    }

    /**
     * 与 {@link #rules()} 一一对应、按需编译并缓存的正则。
     * 127 条正则的编译成本约 55ms，只在第一次匹配文件名时付出一次。
     */
    public static Pattern[] patterns() {
        Pattern[] value = compiled;
        if (value != null) return value;
        synchronized (MediaBadgeCatalog.class) {
            if (compiled == null) {
                Pattern[] next = new Pattern[RULES.length];
                for (int i = 0; i < RULES.length; i++) next[i] = Pattern.compile(RULES[i].pattern());
                compiled = next;
            }
            value = compiled;
        }
        return value;
    }
}
