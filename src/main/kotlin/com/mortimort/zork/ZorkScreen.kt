package com.mortimort.zork

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Zork I for LightOS — a calm, monochrome text adventure. */
@InitialScreen
class ZorkScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, ZorkViewModel>(sealedActivity) {

    override val viewModelClass: Class<ZorkViewModel>
        get() = ZorkViewModel::class.java

    override fun createViewModel(): ZorkViewModel = ZorkViewModel(lightContext.filesDir)

    private fun openSaves() {
        navigateTo(
            screenFactory = { sa -> ZorkSavesScreen(sealedActivity = sa, slots = viewModel.listSlots(), onDelete = { viewModel.deleteSlot(it) }) },
            resultCallback = { resultSlot: Int? ->
                if (resultSlot != null && resultSlot > 0) viewModel.loadSlot(resultSlot)
            },
        )
    }

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.state.collectAsState()

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.SAVE_TO_ALBUM,
                        onClick = { openSaves() },
                    ),
                    center = LightTopBarCenter.Text("Zork I"),
                    rightButton = LightBarButton.LightIcon(
                        icon = LightIcons.SETTINGS,
                        onClick = {
                            navigateTo(
                                screenFactory = { activity -> ZorkAboutScreen(activity, viewModel::restart) },
                                resultCallback = { },
                            )
                        },
                    ),
                )

                Box(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        LightText(
                            text = state.statusLine,
                            variant = LightTextVariant.Detail,
                            lighten = true,
                            align = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 0.5f.gridUnitsAsDp()),
                        )
                        LightScrollView {
                            LightText(
                                text = state.transcript,
                                variant = LightTextVariant.Copy,
                                align = TextAlign.Left,
                                modifier = Modifier.padding(horizontal = 2f.gridUnitsAsDp()),
                            )
                        }
                    }
                }

                LightTextField(
                    label = "Command",
                    value = state.input,
                    placeholder = "Tap to type a command",
                    onClick = {
                        navigateTo(
                            screenFactory = { activity -> ZorkInputScreen(activity, state.input) },
                            resultCallback = { result: String? -> result?.let { viewModel.submit(it) } },
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 1f.gridUnitsAsDp()),
                )
            }
        }
    }
}

/**
 * Full-screen LP3 keyboard editor for a single Zork command. Returns the typed
 * text (or null on back) via the screen result.
 */
class ZorkInputScreen(
    sealedActivity: SealedLightActivity,
    private val initial: String,
) : SimpleLightScreen<String>(sealedActivity) {

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        val state = rememberTextFieldState(initial)
        val keyboardOptionsFlow = rememberKeyboardOptions()
        LightTheme(colors = colors) {
            LightTextInputEditor(
                title = "Command",
                state = state,
                keyboardOptionsFlow = keyboardOptionsFlow,
                onSubmit = { result: CharSequence -> goBack(result.toString().trim()) },
                onBack = { goBack(null) },
                modifier = Modifier.background(LightThemeTokens.colors.background),
            )
        }
    }
}

/**
 * Saves browser — three numbered slots. Tapping a slot loads it (returns the
 * slot number to the host). Each occupied slot has a Delete action.
 *
 * Uses a plain Compose [Row] + [Modifier.clickable] for the back button and
 * delete action because the SDK's [LightTopBar]/[LightBarButton] route through
 * [com.thelightphone.sdk.ui.lightClickable], which no-ops when haptics are
 * disabled (the default on the LP3 emulator).
 */
class ZorkSavesScreen(
    sealedActivity: SealedLightActivity,
    private val slots: List<ZorkEngine.SlotInfo>,
    private val onDelete: (Int) -> Unit,
) : SimpleLightScreen<Int>(sealedActivity) {

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        LightTheme(colors = colors) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LightThemeTokens.colors.background),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { goBack(0) },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        LightText(
                            text = "←",
                            variant = LightTextVariant.Paragraph,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                    LightText(
                        text = "Saves",
                        variant = LightTextVariant.Paragraph,
                        modifier = Modifier
                            .weight(1f)
                            .wrapContentWidth(Alignment.CenterHorizontally),
                    )
                    Spacer(Modifier.weight(1f))
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 1f.gridUnitsAsDp()),
                ) {
                    Spacer(Modifier.height(1f.gridUnitsAsDp()))
                    slots.forEach { info ->
                        val dateStr = if (info.exists && info.lastModified > 0) {
                            try {
                                SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                                    .format(Date(info.lastModified))
                            } catch (_: Exception) { "" }
                        } else ""
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(LightThemeTokens.colors.background)
                                .clickable { goBack(info.slot) }
                                .padding(vertical = 1f.gridUnitsAsDp()),
                        ) {
                            LightText(
                                text = "Slot ${info.slot}",
                                variant = LightTextVariant.Subheading,
                                align = TextAlign.Start,
                            )
                            LightText(
                                text = if (info.exists) "Saved · $dateStr" else "Empty",
                                variant = LightTextVariant.Detail,
                                align = TextAlign.Start,
                            )
                        }
                        if (info.exists) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(LightThemeTokens.colors.background),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onDelete(info.slot); goBack(0) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    LightText(
                                        text = "Delete slot ${info.slot}",
                                        variant = LightTextVariant.Paragraph,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(1f.gridUnitsAsDp()))
                    }
                }
            }
        }
    }
}

/**
 * About page for Zork I. Shows title, copyright, interpreter info, SDK credit,
 * and save-system description.
 *
 * Uses a plain Compose [Row] + [Modifier.clickable] for back and restart
 * instead of the SDK's [LightTopBar]/[LightBarButton], which no-op when haptics
 * are disabled (the default on the LP3 emulator).
 */
class ZorkAboutScreen(
    sealedActivity: SealedLightActivity,
    private val restart: () -> Unit,
) : SimpleLightScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        LightTheme(colors = colors) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar: back button (left) + title
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LightThemeTokens.colors.background),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { goBack(null) },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        LightText(
                            text = "←",
                            variant = LightTextVariant.Paragraph,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                    LightText(
                        text = AboutText.TITLE,
                        variant = LightTextVariant.Paragraph,
                        modifier = Modifier
                            .weight(1f)
                            .wrapContentWidth(Alignment.CenterHorizontally),
                    )
                    Spacer(Modifier.weight(1f))
                }

                // Scrollable body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 2f.gridUnitsAsDp()),
                ) {
                    Spacer(Modifier.height(1f.gridUnitsAsDp()))

                    // ── Story ───────────────────────────────────────────────────
                    sectionHeader("Story")
                    LightText(
                        text = storyBody(),
                        variant = LightTextVariant.Copy,
                        align = TextAlign.Left,
                    )
                    Spacer(Modifier.height(1f.gridUnitsAsDp()))

                    // ── Save system ──────────────────────────────────────────────
                    sectionHeader("Save system")
                    LightText(
                        text = """Three numbered slots — type "save" in the game to write the current state to the next free slot, or tap the save icon in the top-left corner of the game screen.

Each slot keeps both the Z-machine state and the visible transcript, so reloading a slot shows the same screen you left.

Tap the Saves menu to browse, load, or delete individual slots.""",
                        variant = LightTextVariant.Copy,
                        align = TextAlign.Left,
                    )
                    Spacer(Modifier.height(2f.gridUnitsAsDp()))

                    // ── Restart ─────────────────────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(LightThemeTokens.colors.background),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { restart(); goBack(null) },
                            contentAlignment = Alignment.Center,
                        ) {
                            LightText(
                                text = "Restart",
                                variant = LightTextVariant.Paragraph,
                            )
                        }
                    }
                }

                // Version caption at the bottom
                LightText(
                    text = "Version ${AboutText.VERSION}",
                    variant = LightTextVariant.Fine,
                    align = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2f.gridUnitsAsDp()),
                )
            }
        }
    }

    @Composable
    private fun sectionHeader(text: String) {
        LightText(
            text = text.uppercase(),
            variant = LightTextVariant.Fine,
            align = TextAlign.Start,
            modifier = Modifier.padding(top = 2f.gridUnitsAsDp(), bottom = 0.5f.gridUnitsAsDp()),
        )
    }

    private fun storyBody(): String {
        // Strip the "Save system:" paragraph and trim trailing blank lines.
        return AboutText.CONTENT
            .lines()
            .takeWhile { !it.startsWith("Save system:") }
            .joinToString("\n") + "\n"
    }
}
