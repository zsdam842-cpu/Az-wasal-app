package com.wasal.app

import com.wasal.app.data.network.CoreApiClient
import com.wasal.app.data.network.MediaApiClient
import com.wasal.app.data.network.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerRoutingTest {
    @Test
    fun accountRequestsUseCoreServer() {
        val request = CoreApiClient.get("/api/users/me")
        assertEquals(
            "https://az-wasal-server-production.up.railway.app/api/users/me",
            request.url.toString()
        )
    }

    @Test
    fun callHistoryUsesMediaServerAndPreservesAuthentication() {
        val request = MediaApiClient().get(ServerConfig.CALLS_ENDPOINT, "test-access-token")
        assertEquals(
            "https://az-wasal-media-server-production.up.railway.app/api/calls",
            request.url.toString()
        )
        assertEquals("Bearer test-access-token", request.header("Authorization"))
        assertEquals("application/json", request.header("Accept"))
    }

    @Test
    fun mediaPathsWithoutLeadingSlashAreNormalized() {
        val request = MediaApiClient().get("api/media/upload")
        assertEquals(
            "https://az-wasal-media-server-production.up.railway.app/api/media/upload",
            request.url.toString()
        )
        assertNull(request.header("Authorization"))
    }
}
