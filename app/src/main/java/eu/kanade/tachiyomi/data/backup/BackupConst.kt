package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.BuildConfig.APPLICATION_ID as ID

object BackupConst {

    private const val NAME = "BackupRestoreServices"
    const val EXTRA_URI = "$ID.$NAME.EXTRA_URI"
    const val EXTRA_FLAGS = "$ID.$NAME.EXTRA_FLAGS"
    const val EXTRA_MODE = "$ID.$NAME.EXTRA_MODE"
    const val EXTRA_TYPE = "$ID.$NAME.EXTRA_TYPE"

    const val BACKUP_TYPE_FULL = 1
}
