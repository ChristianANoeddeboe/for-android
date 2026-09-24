package chat.stoat.internals.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionTest {
    @Test
    fun parsesTagAndVersionName() {
        assertEquals(UpdateVersion(1, 7, 2, 2), UpdateVersion.parse("v1.7.2-selfhosted.2"))
        assertEquals(UpdateVersion(1, 7, 2, 2), UpdateVersion.parse("1.7.2-selfhosted.2"))
        assertEquals(UpdateVersion(1, 7, 2, 0), UpdateVersion.parse("1.7.2"))
        assertEquals(UpdateVersion(1, 7, 2, 2), UpdateVersion.parse("1.7.2-selfhosted.2+debug"))
    }

    @Test
    fun rejectsUnknownFormats() {
        assertNull(UpdateVersion.parse("nightly"))
        assertNull(UpdateVersion.parse("1.7"))
        assertNull(UpdateVersion.parse("1.7.2-beta.1"))
    }

    @Test
    fun comparesRevisionsAndUpstreamVersions() {
        assertTrue(UpdateVersion.isNewer("v1.7.2-selfhosted.3", "1.7.2-selfhosted.2"))
        assertTrue(UpdateVersion.isNewer("v1.7.3-selfhosted.1", "1.7.2-selfhosted.9"))
        assertTrue(UpdateVersion.isNewer("v1.7.10-selfhosted.1", "1.7.9-selfhosted.1"))
        assertTrue(UpdateVersion.isNewer("v1.7.2-selfhosted.1", "1.7.2"))
        assertFalse(UpdateVersion.isNewer("v1.7.2-selfhosted.2", "1.7.2-selfhosted.2"))
        assertFalse(UpdateVersion.isNewer("v1.7.2-selfhosted.1", "1.7.2-selfhosted.2"))
        assertFalse(UpdateVersion.isNewer("garbage", "1.7.2-selfhosted.2"))
    }
}
