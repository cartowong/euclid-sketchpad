package euclidsketchpad

import javafx.application.Application
import euclidsketchpad.export.HeadlessExportApp
import euclidsketchpad.ui.SketchpadApp

const val APP_NAME = "EuclidSketchpad"
const val APP_VERSION = "2.3.0"

fun main(args: Array<String>) {
    if (args.isNotEmpty() && args[0] == "--export") {
        HeadlessExportApp.run(args.drop(1).toTypedArray())
    } else {
        Application.launch(SketchpadApp::class.java)
    }
}
