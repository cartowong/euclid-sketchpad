package euclideansketchpad

import javafx.application.Application
import euclideansketchpad.export.HeadlessExportApp
import euclideansketchpad.ui.SketchpadApp

const val APP_NAME = "EuclideanSketchpad"
const val APP_VERSION = "1.5.7"

fun main(args: Array<String>) {
    if (args.isNotEmpty() && args[0] == "--export") {
        HeadlessExportApp.run(args.drop(1).toTypedArray())
    } else {
        Application.launch(SketchpadApp::class.java)
    }
}
