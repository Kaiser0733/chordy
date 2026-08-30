package com.kaiser.chordy

import android.app.Application
import com.kaiser.chordy.audio.AudioPlayer
import com.kaiser.chordy.data.PersonaStore
import com.kaiser.chordy.data.SettingsStore
import com.kaiser.chordy.network.LlmClient
import com.kaiser.chordy.network.TtsClient
import com.kaiser.chordy.overlay.OverlayManager
import com.kaiser.chordy.update.UpdateChecker
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class ChordyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@ChordyApp)
            modules(appModule)
        }
    }

    private val appModule = module {
        single { SettingsStore(androidContext()) }
        single { PersonaStore(androidContext()) }
        single { LlmClient() }
        single { TtsClient() }
        single { AudioPlayer(androidContext()) }
        single { OverlayManager(androidContext()) }
        factory { UpdateChecker(androidContext()) }
    }
}
