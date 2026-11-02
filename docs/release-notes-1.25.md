## Wortschatz 1.25

### 停用词不再一刀切：词库有的词一定导入

`unter`、`nur`、`auf`、`mit`、`über`、`auch`、`aber`、`ich`、`sie`、`haben` 这些词
既在停用词表里，**也是词库正经收录的词条**。早前的过滤不看词库，一律丢弃，
于是教材词表里的 `unter`、`nur` 永远导不进来。

现在改成**先匹配、再决定**：

1. 所有候选词先拿去和词库比对；
2. 命中的词条**一律保留**，不管它在不在停用词表；
3. 只有「停用词表里有、词库也没有」的才丢弃，比如 der / die / das / und / ist。

停用词表现在只作用于「词库本身不收的功能词」，不会再误伤正文词汇。

### 实测

`Mann Frau unter Hobby Job nur Freund spielen machen Yoga`
→ 命中 10 个，`unter`（在……下面）、`nur`（只；仅仅）都在列。

### 仍然会落到「未收录」的三类词

- 数字词：`fünfundvierzig`、`siebenundzwanzig`——词库未收录数词
- 变格形式：`Jahre`（词库收的是 `das Jahr`）、`Kindern`
- 阴性形式：`Friseurin`、`Chefin`、`Architektin`——词库只有 `der Friseur` / `der Chef` / `der Architekt`

这三类可以在「未收录」里直接补填释义入库。
