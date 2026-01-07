//
//  RefocusWidgets.swift
//  RefocusWidgets
//
//  Created by Takechiyo Sakazaki on 2025/12/19.
//

import WidgetKit
import SwiftUI
import ActivityKit

struct RefocusLiveActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: RefocusAttributes.self) { context in
            // Lock Screen / Banner
            VStack(alignment: .leading) {
                Text("Refocus")
                    .font(.headline)

                Text(timerInterval: context.attributes.sessionStart...Date(), countsDown: false)
                    .monospacedDigit()
            }
            .padding()
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Text("Refocus")
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text(timerInterval: context.attributes.sessionStart...Date(), countsDown: false)
                        .monospacedDigit()
                }
            } compactLeading: {
                Text("R")
            } compactTrailing: {
                Text(timerInterval: context.attributes.sessionStart...Date(), countsDown: false)
                    .monospacedDigit()
            } minimal: {
                Text("R")
            }
        }
    }
}
