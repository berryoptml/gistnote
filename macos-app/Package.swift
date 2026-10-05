// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "GistNoteDesktop",
    platforms: [.macOS(.v13)],
    products: [.executable(name: "GistNoteDesktop", targets: ["GistNoteDesktop"])],
    targets: [.executableTarget(name: "GistNoteDesktop")]
)
