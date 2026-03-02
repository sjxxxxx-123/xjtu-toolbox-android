# Miuix-KMP API Reference (from source code)

Source: https://github.com/compose-miuix-ui/miuix

## basic/ package (`top.yukonga.miuix.kmp.basic`)

### Button
```kotlin
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = ButtonDefaults.CornerRadius, // 16.dp
    minWidth: Dp = ButtonDefaults.MinWidth, // 58.dp
    minHeight: Dp = ButtonDefaults.MinHeight, // 40.dp
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    insideMargin: PaddingValues = ButtonDefaults.InsideMargin,
    content: @Composable RowScope.() -> Unit,
)

// ButtonDefaults
fun buttonColors(color, disabledColor)
fun buttonColorsPrimary() // primary color
fun textButtonColors(color, disabledColor, textColor, disabledTextColor)
fun textButtonColorsPrimary()
```

### TextButton
```kotlin
fun TextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: TextButtonColors = ButtonDefaults.textButtonColors(),
    cornerRadius: Dp = ButtonDefaults.CornerRadius,
    minWidth: Dp = ButtonDefaults.MinWidth,
    minHeight: Dp = ButtonDefaults.MinHeight,
    insideMargin: PaddingValues = ButtonDefaults.InsideMargin,
)
```

### Card
```kotlin
fun Card(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = CardDefaults.CornerRadius,
    colors: CardColors = CardDefaults.defaultColors(),
    content: @Composable () -> Unit,
)
// CardDefaults.defaultColors(color = ...) — only `color` parameter
```

### TextField (3 overloads)
```kotlin
// Overload 1: TextFieldState-based (Compose 1.7+)
fun TextField(
    state: TextFieldState,
    modifier: Modifier, insideMargin: DpSize, backgroundColor: Color,
    cornerRadius: Dp = 16.dp, label: String = "", labelColor: Color,
    borderColor: Color, useLabelAsPlaceholder: Boolean = false,
    enabled: Boolean, readOnly: Boolean,
    inputTransformation: InputTransformation?,
    textStyle: TextStyle, keyboardOptions, onKeyboardAction,
    lineLimits: TextFieldLineLimits, leadingIcon, trailingIcon,
    onTextLayout, interactionSource, cursorBrush,
    outputTransformation, scrollState,
)

// Overload 2: TextFieldValue-based
fun TextField(
    value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit,
    modifier, insideMargin, backgroundColor, cornerRadius, label: String,
    labelColor, borderColor, useLabelAsPlaceholder, enabled, readOnly,
    textStyle, keyboardOptions, keyboardActions,
    leadingIcon, trailingIcon, singleLine, maxLines, minLines,
    visualTransformation, onTextLayout, interactionSource, cursorBrush,
)

// Overload 3: String-based ✅ Most commonly used
fun TextField(
    value: String, onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    insideMargin: DpSize = DpSize(16.dp, 16.dp),
    backgroundColor: Color = MiuixTheme.colorScheme.secondaryContainer,
    cornerRadius: Dp = 16.dp,
    label: String = "",
    labelColor: Color = MiuixTheme.colorScheme.onSecondaryContainer,
    borderColor: Color = MiuixTheme.colorScheme.primary,
    useLabelAsPlaceholder: Boolean = false,
    enabled: Boolean = true, readOnly: Boolean = false,
    textStyle: TextStyle = MiuixTheme.textStyles.main,
    keyboardOptions: KeyboardOptions, keyboardActions: KeyboardActions,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    maxLines: Int, minLines: Int,
    visualTransformation: VisualTransformation,
    onTextLayout, interactionSource, cursorBrush,
)
```

### Switch
```kotlin
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    colors: SwitchColors = SwitchDefaults.switchColors(),
    enabled: Boolean = true,
)
```

### Checkbox
```kotlin
fun Checkbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    colors: CheckboxColors = CheckboxDefaults.checkboxColors(),
    enabled: Boolean = true,
)
```

### Slider
```kotlin
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    reverseDirection: Boolean = false,
    height: Dp = SliderDefaults.MinHeight,
    colors: SliderColors = SliderDefaults.sliderColors(),
    hapticEffect: SliderDefaults.SliderHapticEffect,
    showKeyPoints: Boolean = false,
    keyPoints: List<Float>? = null,
    magnetThreshold: Float = 0.02f,
)
```

### Scaffold
```kotlin
fun Scaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    floatingToolbar: @Composable () -> Unit = {},
    floatingToolbarPosition: ToolbarPosition = ToolbarPosition.BottomCenter,
    snackbarHost: @Composable () -> Unit = {},
    popupHost: @Composable () -> Unit = { MiuixPopupHost() },
    containerColor: Color = MiuixTheme.colorScheme.surface,
    contentWindowInsets: WindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
    content: @Composable (PaddingValues) -> Unit,
)
```

### TopAppBar
```kotlin
fun TopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.surface,
    largeTitle: String? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: ScrollBehavior? = null,
    defaultWindowInsetsPadding: Boolean = true,
    horizontalPadding: Dp = 26.dp,
)

fun SmallTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.surface,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: ScrollBehavior? = null,
    defaultWindowInsetsPadding: Boolean = true,
    horizontalPadding: Dp = 26.dp,
)

fun MiuixScrollBehavior(
    state: TopAppBarState = rememberTopAppBarState(),
    canScroll: () -> Boolean = { true },
    snapAnimationSpec: AnimationSpec<Float>? = spring(stiffness = 2500f),
    flingAnimationSpec: DecayAnimationSpec<Float>? = rememberSplineBasedDecay(),
): ScrollBehavior
```

### NavigationBar
```kotlin
fun NavigationBar(
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.surface,
    showDivider: Boolean = true,
    defaultWindowInsetsPadding: Boolean = true,
    mode: NavigationDisplayMode = NavigationDisplayMode.IconAndText,
    content: @Composable RowScope.() -> Unit,
)

fun RowScope.NavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
)
```

### HorizontalDivider / VerticalDivider
```kotlin
fun HorizontalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = DividerDefaults.Thickness, // 0.75.dp
    color: Color = DividerDefaults.DividerColor, // MiuixTheme.colorScheme.dividerLine
)

fun VerticalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = DividerDefaults.Thickness,
    color: Color = DividerDefaults.DividerColor,
)
```

### FloatingActionButton
```kotlin
fun FloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = Capsule(),
    containerColor: Color = MiuixTheme.colorScheme.primary,
    shadowElevation: Dp = 4.dp,
    minWidth: Dp = 60.dp,
    minHeight: Dp = 60.dp,
    content: @Composable () -> Unit,
)
```

### Surface
```kotlin
fun Surface(
    modifier: Modifier, shape: Shape, color: Color, content: @Composable () -> Unit,
)
fun Surface(
    onClick: () -> Unit, modifier: Modifier, enabled: Boolean,
    shape: Shape, color: Color, shadowElevation: Dp, content: @Composable () -> Unit,
)
```

## extra/ package (`top.yukonga.miuix.kmp.extra`)

### SuperDialog
```kotlin
fun SuperDialog(
    show: MutableState<Boolean>,
    modifier: Modifier = Modifier,
    title: String? = null,
    titleColor: Color = SuperDialogDefaults.titleColor(),
    summary: String? = null,
    summaryColor: Color = SuperDialogDefaults.summaryColor(),
    backgroundColor: Color = SuperDialogDefaults.backgroundColor(),
    enableWindowDim: Boolean = true,
    onDismissRequest: (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    outsideMargin: DpSize = SuperDialogDefaults.outsideMargin,
    insideMargin: DpSize = SuperDialogDefaults.insideMargin,
    defaultWindowInsetsPadding: Boolean = true,
    content: @Composable () -> Unit,
)
```

### SuperSpinner
```kotlin
// Popup mode
fun SuperSpinner(
    items: List<SpinnerEntry>,
    selectedIndex: Int,
    title: String,
    modifier: Modifier, titleColor, summary, summaryColor,
    spinnerColors, startAction, bottomAction, insideMargin,
    maxHeight, enabled, showValue, onSelectedIndexChange,
)

// Dialog mode
fun SuperSpinner(
    items: List<SpinnerEntry>,
    selectedIndex: Int,
    title: String,
    dialogButtonString: String,
    ...same as above...
)
```

## Color Mapping (MD3 → Miuix)

| MD3 | Miuix |
|-----|-------|
| `onSurfaceVariant` | `onSurfaceVariantSummary` |
| `outlineVariant` | `outline` |
| `tertiary` | `primaryVariant` |
| `onTertiary` | `onPrimaryVariant` |
| `surfaceContainerLow/Lowest/High/Highest/surfaceContainer` | `surfaceVariant` |
| `scrim` | `windowDimming` |
| `inverseOnSurface` | `onBackground` |
| `inverseSurface` | `background` |

## Typography Mapping (MD3 → Miuix textStyles)

| MD3 | Miuix |
|-----|-------|
| `headlineLarge` | `title3` |
| `headlineMedium` | `title4` |
| `headlineSmall` | `headline1` |
| `titleLarge` | `title4` |
| `titleMedium` | `subtitle` |
| `titleSmall` | `body1` |
| `bodyLarge` | `body1` |
| `bodyMedium` | `body1` |
| `bodySmall` | `body2` |
| `labelLarge` | `subtitle` |
| `labelMedium` | `body2` |
| `labelSmall` | `footnote1` |
| `displayLarge` | `title1` |
| `displayMedium` | `title2` |
| `displaySmall` | `title3` |
