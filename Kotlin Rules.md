# Android Kotlin AI 开发规范

## 核心约束
- 纯 Kotlin 开发，UI 必须使用 Jetpack Compose（禁用 XML）。
- 架构：MVVM + Clean Architecture (Presentation/Domain/Data)。
- 依赖注入：全项目使用 Hilt（@HiltViewModel + @Inject constructor）。
- 状态管理：仅使用 StateFlow / SharedFlow（禁用 LiveData）。

## 编码规范
- 命名：UpperCamelCase (类), lowerCamelCase (函数/变量), UPPER_SNAKE_CASE (常量)。
- 空安全：严禁使用 `!!`，必须用 `?.` 和 `?:` 处理。
- 数据模型：纯数据用 `data class`，状态流转用 `sealed class/interface`。
- 函数：保持简短（<30行），单行返回使用表达式函数体。

## 协程与异步
- 作用域：仅限 viewModelScope / lifecycleScope，严禁 GlobalScope。
- 调度器：IO 操作必须显式 `withContext(Dispatchers.IO)`。
- 错误处理：Domain 层封装 AppResult<T>，ViewModel 映射为 UiState，禁止裸 try-catch。

## 结构与红线
- 包结构：按 Feature 分包（如 feature/cart/ui, feature/cart/viewmodel）。
- Compose 规范：禁止在 Composable 中调用 suspend 函数或创建协程。
- 列表：必须使用 LazyColumn/LazyRow 并提供稳定的 key。
- 内存：禁止在 Lambda 中强引用 Activity/Fragment。