package util

import java.awt.Desktop
import java.net.URI
object Utils{
    fun browseUrl(url: String) {
        val desktop = Desktop.getDesktop()
        desktop.browse(URI.create(url))
    }
}
