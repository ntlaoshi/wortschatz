## Wortschatz 1.16

### 查词界面显示查词记录

查词页在输入框为空时，除了原有的提示与示例词，新增「最近查过」区块：

- 显示最近查过的词条（去重、最近在前，最多保留 20 条）
- 点任意一条可直接再次检索
- 右侧「清除」一键清空
- 记录写入本地偏好，重启后仍在

记录时机：点开某条查询结果、或在键盘上按搜索键，都会把当前查询词记入历史。
单纯输入但不点开、不提交的，不计入，避免把打字过程中的半截前缀塞进历史。

### 实现

- `AppViewModel.searchHistory` 用 SharedPreferences 持久化（分隔符 `\u001F` 拼接）
- `recordSearch()` 去重 + 截断到 20 条；`clearSearchHistory()` 清空
- 空查询分支改为可滚动 Column，提示块下方追加历史块，用 `FlowRow` 流式排布词条 chips

### 版本

versionCode 17 / versionName 1.16
