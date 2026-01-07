//
//  ContentView.swift
//  iOS
//
//  Created by Takechiyo Sakazaki on 2025/12/19.
//

import SwiftUI

struct ContentView: View {
    @State private var manager = SessionManager()

    var body: some View {
        VStack(spacing: 16) {
            Text("Refocus iOS")
                .font(.title)

            if let s = manager.session {
                Text("Running: \(s.isRunning ? "YES" : "NO")")
                Text("Elapsed: \(Int(s.elapsed)) sec")
            } else {
                Text("No session")
            }

            HStack {
                Button("Start") {
                    manager.start()
                    if let start = manager.session?.startAt {
                        LiveActivityController.start(startDate: start)
                    }
                }
                .buttonStyle(.borderedProminent)

                Button("Stop") {
                    manager.stop()
                    LiveActivityController.stopAll()
                }
                .buttonStyle(.bordered)
            }
        }
        .padding()
    }
}
