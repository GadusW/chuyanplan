package com.stillune.app.health

import com.stillune.app.data.Track

internal data class ChineseReading(val heading: String, val note: String, val source: String, val url: String)

internal object ChineseReadingCatalog {
    private data class Entry(val artists: Set<String>, val tracks: Set<String>, val reading: ChineseReading)

    private val jay = setOf("周杰伦", "周杰倫", "Jay Chou")
    private val eason = setOf("陈奕迅", "陳奕迅", "Eason Chan")
    private val omnipotent = setOf("万能青年旅店", "萬能青年旅店", "Omnipotent Youth Society")
    private const val FANTASY = "https://www.sina.cn/news/detail/5342837616938178.html"
    private const val MOUNTAIN = "https://ep.ycwb.com/epaper/ywdf/resfile/2022-03-11/FA15/ycwbdfb20220311FA15.pdf"
    private const val U87 = "https://ep.ycwb.com/epaper/ycwb/resfile/2023-11-06/A07/ycwb20231106A07.pdf"
    private const val HEBEI = "https://www.thepaper.cn/newsDetail_forward_10510619"

    private val entries = listOf(
        Entry(jay, setOf("双截棍", "雙截棍", "Nunchucks"), ChineseReading(
            "咬字也是节奏", "可以先把人声当成打击乐来听。短促的字头和重拍有时一起落下，有时在拍点之间穿插。再听那段突然出现的钢琴，留意它怎样给前后的声音换了一口气。",
            "Billboard 中国 · 《范特西》MV 回顾", FANTASY)),
        Entry(jay, setOf("安静", "安靜", "Silence"), ChineseReading(
            "钢琴给人声留了多少空间", "从开头听起，留意钢琴在一句人声结束后怎样接过旋律。到后面声音渐渐变满时，再比较钢琴和人声之间的距离感。",
            "Billboard 中国 · 《范特西》MV 回顾", FANTASY)),
        Entry(eason, setOf("富士山下"), ChineseReading(
            "从琴声听到弦乐", "先跟着钢琴听，再留意弦乐怎样从人声背后逐渐浮出来。比较两个段落的尾句，听听伴奏有没有给相似的旋律换上不同的重量。",
            "羊城晚报 · 陈珂访谈", MOUNTAIN)),
        Entry(eason, setOf("浮夸", "浮誇"), ChineseReading(
            "把开头和结尾放在一起听", "先记住开头那种收着说话的感觉，再留意后半段声音从哪一句开始绷紧。听到最强的一句时，可以回想前面留了多少空白。",
            "羊城晚报 · 陈奕迅作品回顾", U87)),
        Entry(eason, setOf("葡萄成熟时", "葡萄成熟時"), ChineseReading(
            "同一段旋律怎样唱出两种心情", "副歌回来时，听人声的尾音怎么收住，再比较伴奏有没有把声音推得更满。重复的旋律可以成为对照，帮助你听出唱法和配器的变化。",
            "羊城晚报 · 陈奕迅作品回顾", U87)),
        Entry(eason, setOf("夕阳无限好", "夕陽無限好"), ChineseReading(
            "听旋律在句尾停在哪里", "留意主歌句尾和副歌句尾的不同。听到一个较长的尾音时，继续听下一拍的伴奏，感受它是把乐句收住，还是把它带向下一句。",
            "羊城晚报 · 陈奕迅作品回顾", U87)),
        Entry(omnipotent, setOf("山雀"), ChineseReading(
            "人声与器乐怎样交接", "听唱句结束的地方。人声收住后，试着继续追一件你最先注意到的乐器，看看它是接着讲同一件事，还是把声音带往别处。",
            "澎湃新闻 · 《冀西南林路行》专辑评论", HEBEI)),
        Entry(omnipotent, setOf("采石", "採石"), ChineseReading(
            "跟着一条器乐线听下去", "挑一条你容易辨认的器乐线，听它在织体变厚时还在不在。也可以反过来比较人声出现前后，注意力被哪一种声音带走。",
            "澎湃新闻 · 《冀西南林路行》专辑评论", HEBEI)),
        Entry(omnipotent, setOf("河北墨麒麟"), ChineseReading(
            "用重复找方向", "长篇幅里，先记住一个你能辨认的旋律或节奏。它再次出现时，比较周围多了哪些声音，听同一个线索在不同段落里怎样改变气氛。",
            "澎湃新闻 · 《冀西南林路行》专辑评论", HEBEI)),
        Entry(omnipotent, setOf("泥河"), ChineseReading(
            "听一听声音的远近", "先听最贴近耳边的一层，再把注意力转向它后面的声音。段落推进时，留意哪一层退下去，哪一层来到前景。",
            "澎湃新闻 · 《冀西南林路行》专辑评论", HEBEI)),
        Entry(setOf("许巍", "許巍", "Xu Wei"), setOf("蓝莲花", "藍蓮花"), ChineseReading(
            "先听旋律怎样站起来", "开头可以只跟着人声听，记住旋律在哪些字上抬高。伴奏进来后再听一遍，留意同一条旋律怎样从独唱的感觉变成更开阔的声音。",
            "澎湃新闻 · 许巍访谈", "https://www.thepaper.cn/newsDetail_forward_21198924")),
        Entry(setOf("草东没有派对", "草東沒有派對", "No Party for Cao Dong"), setOf("山海"), ChineseReading(
            "在安静的地方听张力", "把注意力放在弱声部，感受人声和伴奏有多克制。声音突然变满时，再回想它之前用了多长时间积累张力。",
            "澎湃新闻 · 《丑奴儿》乐评", "https://www.thepaper.cn/newsDetail_forward_1507016")),
        Entry(setOf("草东没有派对", "草東沒有派對", "No Party for Cao Dong"), setOf("情歌"), ChineseReading(
            "留意声音和情绪的反差", "先跟着旋律走，再留意伴奏变重时，人声是继续贴着旋律，还是像要从它里面挣出来。两种听法可能让你注意到不同的段落。",
            "澎湃新闻 · 《丑奴儿》乐评", "https://www.thepaper.cn/newsDetail_forward_1507016")),
    )

    fun find(track: Track): ChineseReading? = entries.firstOrNull { entry ->
        entry.artists.any { it.equals(track.artist.trim(), ignoreCase = true) } &&
            entry.tracks.any { it.equals(track.title.trim(), ignoreCase = true) }
    }?.reading
}
