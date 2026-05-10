// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.Manifest
import android.provider.Settings as AndroidSettings
import helium314.keyboard.latin.settings.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.permissions.PermissionsUtil
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.UncachedInputMethodManagerUtils
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.preferences.ListPreference
import helium314.keyboard.settings.preferences.LoadEmojiLibPreference
import helium314.keyboard.settings.preferences.LoadGestureLibPreference
import helium314.keyboard.settings.preferences.MultiSliderPreference
import helium314.keyboard.settings.preferences.SwitchPreference
import helium314.keyboard.settings.preferences.TextInputPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun WelcomeWizard(
    close: () -> Unit,
    finish: () -> Unit
) {
    val ctx = LocalContext.current
    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    fun determineStep(): Int = when {
        !UncachedInputMethodManagerUtils.isThisImeEnabled(ctx, imm) -> 0
        !UncachedInputMethodManagerUtils.isThisImeCurrent(ctx, imm) -> 2
        else -> 3
    }
    var step by rememberSaveable { mutableIntStateOf(determineStep()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(step) {
        if (step == 2)
            scope.launch {
                while (step == 2 && !UncachedInputMethodManagerUtils.isThisImeCurrent(ctx, imm)) {
                    delay(50)
                }
                step = 3
            }
    }
    val useWideLayout = isWideScreen()
    val stepBackgroundColor = Color(ContextCompat.getColor(ctx, R.color.setup_step_background))
    val textColor = Color(ContextCompat.getColor(ctx, R.color.setup_text_action))
    val textColorDim = textColor.copy(alpha = 0.5f)
    val titleColor = Color(ContextCompat.getColor(ctx, R.color.setup_text_title))
    val appName = stringResource(ctx.applicationInfo.labelRes)
    @Composable fun bigText() {
        val resource = if (step == 0) R.string.setup_welcome_title else R.string.setup_steps_title
        Column(Modifier.padding(bottom = 36.dp)) {
            Text(
                stringResource(resource, appName),
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center,
                color = titleColor,
            )
            if (JniUtils.sHaveGestureLib)
                Text(
                    stringResource(R.string.setup_welcome_additional_description),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.End,
                    color = titleColor,
                    modifier = Modifier.fillMaxWidth()
                )
        }
    }
    @Composable
    fun ColumnScope.Step(currentStep: Int, title: String, instruction: String, actionText: String, icon: Painter, action: () -> Unit, content: @Composable () -> Unit = {}) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("1", color = if (currentStep == 1) titleColor else textColorDim)
            Text("2", color = if (currentStep == 2) titleColor else textColorDim)
            Text("3+", color = if (currentStep >= 3) titleColor else textColorDim)
        }
        Column(Modifier
            .background(color = stepBackgroundColor)
            .padding(16.dp)
        ) {
            Text(title)
            Text(instruction, style = MaterialTheme.typography.bodyLarge.merge(color = textColor))
            Spacer(Modifier.height(8.dp))
            content()
        }
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.clickable { action() }
                .background(color = stepBackgroundColor)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, Modifier.padding(end = 6.dp).size(32.dp), tint = textColor)
            Text(actionText, Modifier.weight(1f))
            if (currentStep >= 3 && currentStep < 8) {
                Text(stringResource(android.R.string.cancel), color = textColorDim, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
    @Composable fun steps() {
        if (step == 0)
            Step0 { step = 1 }
        else
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    step = determineStep()
                }
                if (step == 1) {
                    Step(
                        step,
                        stringResource(R.string.setup_step1_title, appName),
                        stringResource(R.string.setup_step1_instruction, appName),
                        stringResource(R.string.setup_step1_action),
                        painterResource(R.drawable.ic_setup_key),
                        {
                            val intent = Intent()
                            intent.action = AndroidSettings.ACTION_INPUT_METHOD_SETTINGS
                            intent.addCategory(Intent.CATEGORY_DEFAULT)
                            launcher.launch(intent)
                        }
                    )
                } else if (step == 2) {
                    Step(
                        step,
                        stringResource(R.string.setup_step2_title, appName),
                        stringResource(R.string.setup_step2_instruction, appName),
                        stringResource(R.string.setup_step2_action),
                        painterResource(R.drawable.ic_setup_select),
                        { imm.showInputMethodPicker() }
                    )
                } else if (step == 3) {
                    Step(
                        step,
                        "Libraries",
                        "Download emoji and gesture libraries to improve typing and suggestions.",
                        "Next",
                        painterResource(R.drawable.sym_keyboard_language_switch),
                        { step++ }
                    ) {
                        LoadEmojiLibPreference("Emoji Dictionary")
                        LoadGestureLibPreference("Gesture Typing Library")
                    }
                } else if (step == 4) {
                    Step(
                        step,
                        "AI Integration",
                        "Select an AI service and provide your API key for advanced proofreading features.",
                        "Next",
                        painterResource(R.drawable.sym_keyboard_language_switch),
                        { step++ }
                    ) {
                        if (BuildConfig.FLAVOR == "standard") {
                            helium314.keyboard.settings.Setting(ctx, helium314.keyboard.settings.SettingsWithoutKey.AI_PROVIDER, R.string.ai_provider_title, R.string.ai_provider_summary) { setting ->
                                ListPreference(setting, listOf(
                                    ctx.getString(R.string.ai_provider_huggingface) to helium314.keyboard.latin.utils.ProofreadService.AIProvider.GROQ.name,
                                    ctx.getString(R.string.ai_provider_gemini) to helium314.keyboard.latin.utils.ProofreadService.AIProvider.GEMINI.name,
                                    ctx.getString(R.string.ai_provider_openai) to helium314.keyboard.latin.utils.ProofreadService.AIProvider.OPENAI.name
                                ), helium314.keyboard.latin.utils.ProofreadService.AIProvider.GEMINI.name)
                            }.Preference()
                            helium314.keyboard.settings.Setting(ctx, helium314.keyboard.settings.SettingsWithoutKey.GEMINI_API_KEY, R.string.gemini_api_key_title, R.string.gemini_api_key_summary) { setting ->
                                TextInputPreference(setting, "")
                            }.Preference()
                        } else {
                            Text("AI features are not available in this build flavor.", color = textColorDim)
                        }
                    }
                } else if (step == 5) {
                    Step(
                        step,
                        "Floating Keyboard",
                        "Enable floating keyboard by granting the 'Display over other apps' permission.",
                        "Next",
                        painterResource(R.drawable.sym_keyboard_language_switch),
                        { step++ }
                    ) {
                        val canDrawOverlays = AndroidSettings.canDrawOverlays(ctx)
                        val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
                        if (!canDrawOverlays) {
                            Text("Permission required. Tap here to grant.", Modifier.clickable {
                                val intent = Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                                overlayLauncher.launch(intent)
                            }.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.primary)
                        } else {
                            Text("Permission granted.", color = textColor)
                        }
                    }
                } else if (step == 6) {
                    Step(
                        step,
                        "Screenshot Suggestions",
                        "Suggest recently taken screenshots in the suggestion strip.",
                        "Next",
                        painterResource(R.drawable.sym_keyboard_language_switch),
                        { step++ }
                    ) {
                        helium314.keyboard.settings.Setting(ctx, Settings.PREF_SUGGEST_SCREENSHOTS, R.string.suggest_screenshots, R.string.suggest_screenshots_summary) { setting ->
                            val activity = ctx.getActivity() ?: return@Setting
                            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                Manifest.permission.READ_MEDIA_IMAGES
                            } else {
                                Manifest.permission.READ_EXTERNAL_STORAGE
                            }
                            var granted by remember { mutableStateOf(PermissionsUtil.checkAllPermissionsGranted(activity, permission)) }
                            val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                                granted = it
                                if (granted)
                                    activity.prefs().edit { putBoolean(setting.key, true) }
                            }
                            SwitchPreference(setting, Defaults.PREF_SUGGEST_SCREENSHOTS, allowCheckedChange = {
                                if (it && !granted) {
                                    permLauncher.launch(permission)
                                    false
                                } else true
                            })
                        }.Preference()
                    }
                } else if (step == 7) {
                    Step(
                        step,
                        "Keyboard Height",
                        "Adjust the height of the keyboard.",
                        "Next",
                        painterResource(R.drawable.sym_keyboard_language_switch),
                        { step++ }
                    ) {
                        helium314.keyboard.settings.Setting(ctx, Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX, R.string.prefs_keyboard_height_scale) { setting ->
                            MultiSliderPreference(
                                name = setting.title,
                                baseKey = setting.key,
                                dimensions = listOf(stringResource(R.string.landscape)),
                                defaults = Defaults.PREF_KEYBOARD_HEIGHT_SCALE,
                                range = 0.3f..1.5f,
                                description = { "${(100 * it).toInt()}%" }
                            ) {}
                        }.Preference()
                    }
                } else { // step 8
                    Step(
                        step,
                        stringResource(R.string.setup_step3_title),
                        stringResource(R.string.setup_step3_instruction, appName),
                        stringResource(R.string.setup_finish_action),
                        painterResource(R.drawable.ic_setup_check),
                        finish
                    )
                }
            }
    }
    Surface {
        CompositionLocalProvider(
            LocalContentColor provides textColor,
            LocalTextStyle provides MaterialTheme.typography.titleLarge.merge(color = textColor),
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                if (useWideLayout)
                    Row {
                        Box(Modifier.weight(0.4f)) {
                            bigText()
                        }
                        Box(Modifier.weight(0.6f)) {
                            steps()
                        }
                    }
                else
                    Column {
                        bigText()
                        steps()
                    }
            }
        }
    }
}

@Composable
fun Step0(onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.setup_welcome_image), null)
        Row(Modifier.clickable { onClick() }
            .padding(top = 4.dp, start = 4.dp, end = 4.dp)
            //.background(color = MaterialTheme.colorScheme.primary)
        ) {
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.setup_start_action),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Preview
@Composable
private fun Preview() {
    Theme(previewDark) {
        Surface {
            WelcomeWizard({}) {  }
        }
    }
}

@Preview(
    // content cut off on real device, but not here... great?
    device = "spec:orientation=landscape,width=400dp,height=780dp"
)
@Composable
private fun WidePreview() {
    Theme(previewDark) {
        Surface {
            WelcomeWizard({}) {  }
        }
    }
}
