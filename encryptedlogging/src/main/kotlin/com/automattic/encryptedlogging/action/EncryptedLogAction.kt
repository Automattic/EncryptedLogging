package com.automattic.encryptedlogging.action

import com.automattic.encryptedlogging.store.EncryptedLogStore.UploadEncryptedLogPayload
import org.wordpress.android.fluxc.annotations.Action
import org.wordpress.android.fluxc.annotations.ActionEnum
import org.wordpress.android.fluxc.annotations.action.IAction

@ActionEnum
internal enum class EncryptedLogAction : IAction {
    @Action(payloadType = UploadEncryptedLogPayload::class)
    UPLOAD_LOG,
    @Action
    RESET_UPLOAD_STATES
}
