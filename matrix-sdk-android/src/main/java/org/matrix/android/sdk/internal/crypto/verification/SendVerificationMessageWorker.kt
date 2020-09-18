/*
 * Copyright 2020 New Vector Ltd
 * Copyright 2020 The Matrix.org Foundation C.I.C.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.matrix.android.sdk.internal.crypto.verification

import android.content.Context
import androidx.work.Data
import androidx.work.WorkerParameters
import com.squareup.moshi.JsonClass
import org.matrix.android.sdk.api.failure.shouldBeRetried
import org.matrix.android.sdk.api.session.crypto.CryptoService
import org.matrix.android.sdk.api.session.events.model.Event
import org.matrix.android.sdk.internal.crypto.tasks.SendVerificationMessageTask
import org.matrix.android.sdk.internal.session.SessionComponent
import org.matrix.android.sdk.internal.worker.MatrixCoroutineWorker
import org.matrix.android.sdk.internal.worker.SessionWorkerParams
import org.matrix.android.sdk.internal.worker.WorkerParamsFactory
import javax.inject.Inject

/**
 * Possible previous worker: None
 * Possible next worker    : None
 */
internal class SendVerificationMessageWorker(context: Context,
                                             params: WorkerParameters)
    : MatrixCoroutineWorker<SendVerificationMessageWorker.Params>(context, params) {

    @JsonClass(generateAdapter = true)
    internal data class Params(
            override val sessionId: String,
            val event: Event,
            override val lastFailureMessage: String? = null
    ) : SessionWorkerParams

    @Inject
    lateinit var sendVerificationMessageTask: SendVerificationMessageTask

    @Inject
    lateinit var cryptoService: CryptoService

    override fun parse(inputData: Data) = WorkerParamsFactory.fromData<Params>(inputData)

    override suspend fun doSafeWork(sessionComponent: SessionComponent, params: Params): Result {
        sessionComponent.inject(this)
        val localId = params.event.eventId ?: ""
        return try {
            val eventId = sendVerificationMessageTask.execute(
                    SendVerificationMessageTask.Params(
                            event = params.event,
                            cryptoService = cryptoService
                    )
            )

            Result.success(Data.Builder().putString(localId, eventId).build())
        } catch (throwable: Throwable) {
            if (throwable.shouldBeRetried()) {
                Result.retry()
            } else {
                buildErrorResult(params, throwable.localizedMessage ?: "error")
            }
        }
    }

    override fun buildErrorResult(params: Params?, message: String): Result {
        return Result.success(
                WorkerParamsFactory.toData(
                        params?.copy(lastFailureMessage = params.lastFailureMessage ?: message)
                                ?: ErrorData(sessionId = "", lastFailureMessage = message)
                )
        )
    }
}
