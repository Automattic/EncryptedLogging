package com.automattic.encryptedlogging.action

import com.automattic.encryptedlogging.annotations.Action
import com.automattic.encryptedlogging.annotations.ActionEnum
import com.automattic.encryptedlogging.annotations.action.IAction
import com.automattic.encryptedlogging.store.EncryptedLogStore.UploadEncryptedLogPayload

@ActionEnum
enum class EncryptedLogAction : IAction {
    @Action(payloadType = UploadEncryptedLogPayload::class)
    UPLOAD_LOG
}
