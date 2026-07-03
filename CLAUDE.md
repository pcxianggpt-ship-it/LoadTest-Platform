## 协作规范

### 文件换行符
- 仓库所有文本文件统一 LF，禁止 CRLF
- 根目录 `.gitattributes` 配 `* text=auto eol=lf`，git 自动归一化
- AI 创建/修改文本文件必须用 LF

### Git 提交规范
- commit message 必须用中文
- 格式：`<类型>：<描述>`，类型参考 feat/fix/docs/refactor/test/chore/perf/style
- 标题 ≤50 字；多项改动拆分多次提交，每次语义单一

### Markdown 文件命名
- AI 生成的 `.md` 文件名必须用中文解释文件含义
- AI 生成的 `.md` 文件名必须带上项目版本号，用于区分不同版本的文档
- 推荐格式：`YYYY-MM-DD-v版本号-中文含义.md`

### 命令执行白名单
- 只读/安全命令直接执行
- 破坏性命令（git push --force / git reset --hard / git rebase / rm -rf / 删除文件）必须先确认
- settings.local.json 的通配放行 ≠ AI 跳过确认，AI 仍须主动识别破坏性子命令

### AI 代码克制
- 不过度设计、不加未被要求的注释/依赖、不做未被要求的重构
- 修改前先读文件，遵循现有风格
- 不主动创建非必要文件，除非明确要求

### AI 指令文件同步
- 修改 CLAUDE.md 协作规范时同步更新 AGENTS.md，同一次提交
