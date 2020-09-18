/*
 * Copyright (c) 2020 New Vector Ltd
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

package org.matrix.android.sdk.internal.worker

import android.content.Context
import androidx.annotation.CallSuper
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.squareup.moshi.JsonClass
import org.matrix.android.sdk.internal.session.SessionComponent
import timber.log.Timber

internal abstract class MatrixCoroutineWorker<PARAM : SessionWorkerParams>(
        context: Context,
        workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters) {

    @JsonClass(generateAdapter = true)
    internal data class ErrorData(
            override val sessionId: String,
            override val lastFailureMessage: String? = null
    ) : SessionWorkerParams

    final override suspend fun doWork(): Result {
        val params = parse(inputData)
                ?: return buildErrorResult(null, "Unable to parse work parameters")
                        .also { Timber.e("Unable to parse work parameters") }

        if (params.lastFailureMessage != null) {
            return doOnError(params)
        }

        val sessionComponent = getSessionComponent(params.sessionId)
                ?: return buildErrorResult(params, "No session")

        return try {
            doSafeWork(sessionComponent, params)
        } catch (throwable: Throwable) {
            buildErrorResult(params, throwable.localizedMessage ?: "error")
        }
    }

    abstract fun parse(inputData: Data): PARAM?

    abstract suspend fun doSafeWork(sessionComponent: SessionComponent, params: PARAM): Result

    abstract fun buildErrorResult(params: PARAM?, message: String): Result

    @CallSuper
    open fun doOnError(params: PARAM): Result {
        // Transmit the error
        return Result.success(inputData)
                .also { Timber.e("Work cancelled due to input error from parent") }
    }

    companion object {
        fun hasFailed(outputData: Data): Boolean {
            return WorkerParamsFactory.fromData<ErrorData>(outputData)
                    .let { it?.lastFailureMessage != null }
        }
    }
}
