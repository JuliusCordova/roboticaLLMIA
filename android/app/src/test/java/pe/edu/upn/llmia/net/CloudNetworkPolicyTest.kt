package pe.edu.upn.llmia.net

import org.junit.Assert.*
import org.junit.Test

class CloudNetworkPolicyTest {
    @Test fun normalDebugRequiresCellular() {
        assertNull(CloudNetworkPolicy.select(null, "wifi", "robot", debug = true, enabled = false))
    }
    @Test fun optInDebugCanUseEmulatorInternet() {
        assertEquals("emulator", CloudNetworkPolicy.select(null, "emulator", "robot", debug = true, enabled = true))
    }
    @Test fun releaseCannotEnableFallback() {
        assertNull(CloudNetworkPolicy.select(null, "wifi", "robot", debug = false, enabled = true))
    }
    @Test fun neverUsesRobotNetworkAsFallback() {
        assertNull(CloudNetworkPolicy.select(null, "robot", "robot", debug = true, enabled = true))
    }
    @Test fun cellularStillTakesPriority() {
        assertEquals("cell", CloudNetworkPolicy.select("cell", "wifi", "robot", debug = true, enabled = true))
    }
    @Test fun offlineHasNoFallback() {
        assertNull(CloudNetworkPolicy.select<String>(null, null, "robot", debug = true, enabled = true))
    }
}
