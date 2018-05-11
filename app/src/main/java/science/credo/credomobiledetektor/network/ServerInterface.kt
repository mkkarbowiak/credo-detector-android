package science.credo.credomobiledetektor.network

import android.content.Context
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.Response
import science.credo.credomobiledetektor.database.CachedMessage
import science.credo.credomobiledetektor.database.DataManager
import science.credo.credomobiledetektor.database.UserInfoWrapper
import science.credo.credomobiledetektor.network.exceptions.*
import science.credo.credomobiledetektor.network.message.out.OutFrame
import science.credo.credomobiledetektor.network.messages.*
import java.io.IOException

/**
 * This class is a bridge between application and external API service.
 *
 * @property mMapper JSON object mapper used to serialize objects to JSON and deserialize JSON string to objects.
 */
class ServerInterface(val context: Context) {
    private val mMapper = jacksonObjectMapper()

    /**
     * Sends request and converts response to object or handles errors based on status code.
     *
     * @param endpoint Endpoint that receives the request.
     * @param outFrame outFrame object, contains request data.
     */
    private inline fun <reified T : Any> sendAndGetResponse(endpoint: String, outFrame: Any): T {
        val pref = UserInfoWrapper(context)
        val response =
            NetworkCommunication.post(endpoint, mMapper.writeValueAsString(outFrame), pref.token)
        when (response.code) {
            in 200..299 -> return mMapper.readValue(response.message)
            else -> throw throwError(response)
        }
    }

    /**
     * Sends request without returning response object.
     *
     * @param endpoint Endpoint that receives the request.
     * @param outFrame outFrame object, contains request data.
     * @throws Exception corresponding to error code.
     */
    private fun sendAndGetNoContent(endpoint: String, outFrame: Any) {
        val pref = UserInfoWrapper(context)
        val response =
            NetworkCommunication.post(endpoint, mMapper.writeValueAsString(outFrame), pref.token)
        when (response.code) {
            in 200..299 -> return
            else -> throw throwError(response)
        }
    }

    /**
     * Throws an error based on status code.
     *
     * @param response Response object.
     * @return Exception
     */
    private fun throwError(response: NetworkCommunication.Response): Exception {
        val message = extractJsonMessage(response.message)

        return when (response.code) {
            400 -> BadRequestException(message)
            401 -> UnauthorizedException(message)
            403 -> ForbiddenException(message)
            404 -> NotFoundException(message)
            500 -> InternalServerErrorException(message)
            else -> ServerException(response.code, message)
        }
    }

    /**
     * Extracts error message from JSON string.
     *
     * @param message JSON string.
     * @return String with error message.
     */
    private fun extractJsonMessage(message: String): String {
        try {
            return mMapper.readValue<ErrorMessage>(message).message
        } catch (e: Exception) {
            return message
        }
    }

    /**
     * Handles user login.
     *
     * @param request BaseLoginRequest object, contains login credentials.
     * @return LoginResponse object
     */
    fun login(request: BaseLoginRequest): LoginResponse {
        return sendAndGetResponse("/user/login", request)
    }

    /**
     * Handles user registration.
     *
     * @param request RegisterRequest object, contains registration fields.
     */
    fun register(request: RegisterRequest) {
        return sendAndGetNoContent("/user/register", request)
    }

    fun ping(request: PingRequest) {
        try {
            return sendAndGetNoContent("/ping", request)
        } catch (e: IOException) {
            cacheMessage("/ping", request)
        }
    }

    fun sendDetections(request: DetectionRequest) {
        try {
            return sendAndGetResponse("/detection", request)
        } catch (e: IOException) {
            cacheMessage("/detection", request)
        }
    }

    fun cacheMessage(endpoint: String, request: Any) {
        val token = UserInfoWrapper(context).token
        val msg = CachedMessage(endpoint, mMapper.writeValueAsString(request), token)
        DataManager.getInstance(context).storeCachedMessage(msg)
    }

    companion object {
        /**
         * @return default instance of ServerInterface.
         */
        fun getDefault(context: Context): ServerInterface {
            return ServerInterface(context)
        }
    }

    /**
     * Wrapper class used to extract error message.
     *
     * @property message Message to be extracted.
     */
    class ErrorMessage(val message: String)
}
