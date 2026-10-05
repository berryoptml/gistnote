import SwiftUI
import AppKit

struct Note: Identifiable, Equatable {
    let id: String
    var title: String
    var markdown: String
    var color: Color
}

@MainActor final class NoteStore: ObservableObject {
    @Published var notes: [Note] = [
        Note(id: "today", title: "Today", markdown: "# Today\n\n- [ ] Review the launch checklist\n- [ ] Send the project update\n- [x] Make coffee", color: Color(red: 1, green: 0.92, blue: 0.55)),
        Note(id: "ideas", title: "Ideas", markdown: "# Ideas\n\n- [ ] Desktop companion\n- [ ] Quick capture shortcut\n\nA small note that stays close.", color: Color(red: 0.78, green: 0.9, blue: 1)),
        Note(id: "reading", title: "Reading list", markdown: "# Reading list\n\n- [ ] Finish the paper\n- [x] Save the key theorem", color: Color(red: 0.88, green: 0.8, blue: 0.95))
    ]

    func toggle(_ noteID: String, line: Int) {
        guard let index = notes.firstIndex(where: { $0.id == noteID }) else { return }
        var lines = notes[index].markdown.components(separatedBy: "\n")
        guard line < lines.count, let marker = lines[line].range(of: "[ ]") ?? lines[line].range(of: "[x]") ?? lines[line].range(of: "[X]") else { return }
        let checked = String(lines[line][marker]) != "[ ]"
        lines[line].replaceSubrange(marker, with: checked ? "[ ]" : "[x]")
        notes[index].markdown = lines.joined(separator: "\n")
    }
}

struct NotePanel: View {
    @ObservedObject var store: NoteStore
    let noteID: String
    @Binding var editing: Bool
    @Binding var expanded: Bool
    @Binding var selectedID: String
    @State private var draft = ""

    var note: Note { store.notes.first(where: { $0.id == noteID })! }
    var lines: [String] { note.markdown.components(separatedBy: "\n") }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(note.title).font(.headline)
                Spacer()
                Button(editing ? "Done" : "Edit") {
                    if editing { save() } else { draft = note.markdown }
                    editing.toggle()
                }.buttonStyle(.borderless)
                Button("⋯") { selectedID = noteID }.buttonStyle(.borderless)
            }
            if editing {
                TextEditor(text: $draft).font(.system(.body, design: .monospaced)).scrollContentBackground(.hidden)
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 8) {
                        ForEach(Array(lines.enumerated()), id: \.offset) { index, line in
                            if let task = taskParts(line) {
                                Button { store.toggle(noteID, line: index) } label: {
                                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                                        Image(systemName: task.checked ? "checkmark.square.fill" : "square")
                                        Text(task.text).strikethrough(task.checked).opacity(task.checked ? 0.55 : 1)
                                        Spacer()
                                    }
                                }.buttonStyle(.plain)
                            } else if !line.isEmpty {
                                Text(line.replacingOccurrences(of: "#", with: "")).font(line.hasPrefix("#") ? .title3.bold() : .body)
                            }
                        }
                    }.frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            HStack { Text("Local cache · ready to sync").font(.caption).opacity(0.55); Spacer(); Text("GistNote").font(.caption2).opacity(0.4) }
        }
        .padding(18).frame(minWidth: 280, minHeight: 190)
        .background(note.color).clipShape(RoundedRectangle(cornerRadius: 18))
        .shadow(color: .black.opacity(0.22), radius: 14, y: 6)
        .onChange(of: editing) { _, isEditing in if !isEditing { save() } }
    }

    private func save() { guard editing == false || draft != note.markdown else { return }; if let i = store.notes.firstIndex(where: { $0.id == noteID }) { store.notes[i].markdown = draft } }
    private func taskParts(_ line: String) -> (checked: Bool, text: String)? {
        guard let range = line.range(of: #"\[[ xX]\]"#, options: .regularExpression) else { return nil }
        let suffixStart = range.upperBound
        let suffix = suffixStart < line.endIndex ? String(line[suffixStart...]) : ""
        return (String(line[range]).lowercased() == "[x]", suffix.trimmingCharacters(in: .whitespaces))
    }
}

struct ContentView: View {
    @StateObject private var store = NoteStore()
    @State private var selectedID = "today"
    @State private var expanded = false
    @State private var editing = false
    @State private var layout = "grid"

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Group {
                if expanded { expandedView } else { NotePanel(store: store, noteID: selectedID, editing: $editing, expanded: $expanded, selectedID: $selectedID) }
            }.padding(12)
            if !expanded { controls }
        }.frame(minWidth: expanded ? 760 : 330, minHeight: expanded ? 520 : 250)
        .background(WindowConfigurator())
    }

    private var controls: some View {
        HStack(spacing: 6) {
            Button("▣") { expanded = true }.help("Expand stack")
            Menu { ForEach(store.notes) { note in Button(note.title) { selectedID = note.id } } } label: { Image(systemName: "square.stack.3d.up") }
        }.buttonStyle(.borderless).padding(18)
    }

    private var expandedView: some View {
        VStack { HStack { Text("GistNote stack").font(.title2.bold()); Spacer(); Picker("Layout", selection: $layout) { Text("Grid").tag("grid"); Text("Horizontal").tag("horizontal"); Text("Vertical").tag("vertical") }.pickerStyle(.segmented).frame(width: 250); Button("Collapse") { expanded = false }.keyboardShortcut(.escape) }.padding(.horizontal, 18)
            Group { if layout == "vertical" { VStack { panels } } else if layout == "horizontal" { HStack { panels } } else { LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())]) { panels } } }.padding(.horizontal, 6) }
    }
    private var panels: some View { ForEach(store.notes) { note in NotePanel(store: store, noteID: note.id, editing: .constant(false), expanded: $expanded, selectedID: $selectedID) } }
}

/// Keeps the note surface usable as a desktop widget instead of a normal document window.
struct WindowConfigurator: NSViewRepresentable {
    func makeNSView(context: Context) -> NSView { ConfiguringView() }
    func updateNSView(_ nsView: NSView, context: Context) { }

    final class ConfiguringView: NSView {
        override func viewDidMoveToWindow() {
            super.viewDidMoveToWindow()
            guard let window else { return }
            window.level = .floating
            window.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary]
            window.isMovableByWindowBackground = true
            window.backgroundColor = .clear
            window.isOpaque = false
        }
    }
}

@main struct GistNoteDesktopApp: App {
    var body: some Scene { WindowGroup("GistNote") { ContentView() }.windowStyle(.hiddenTitleBar).defaultSize(width: 360, height: 280) }
}
