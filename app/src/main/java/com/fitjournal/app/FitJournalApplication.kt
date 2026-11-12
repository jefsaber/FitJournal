package com.fitjournal.app

import android.app.Application
import com.fitjournal.app.core.data.AppContainer

class FitJournalApplication : Application() {
    val container by lazy { AppContainer(this) }
}
