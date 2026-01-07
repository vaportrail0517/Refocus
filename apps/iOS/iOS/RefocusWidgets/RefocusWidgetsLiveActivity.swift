//
//  RefocusWidgetsLiveActivity.swift
//  RefocusWidgets
//
//  Created by Takechiyo Sakazaki on 2025/12/19.
//

import ActivityKit
import WidgetKit
import SwiftUI

struct RefocusWidgetsAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        // Dynamic stateful properties about your activity go here!
        var emoji: String
    }

    // Fixed non-changing properties about your activity go here!
    var name: String
}

struct RefocusWidgetsLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: RefocusWidgetsAttributes.self) { context in
            // Lock screen/banner UI goes here
            VStack {
                Text("Hello \(context.state.emoji)")
            }
            .activityBackgroundTint(Color.cyan)
            .activitySystemActionForegroundColor(Color.black)

        } dynamicIsland: { context in
            DynamicIsland {
                // Expanded UI goes here.  Compose the expanded UI through
                // various regions, like leading/trailing/center/bottom
                DynamicIslandExpandedRegion(.leading) {
                    Text("Leading")
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text("Trailing")
                }
                DynamicIslandExpandedRegion(.bottom) {
                    Text("Bottom \(context.state.emoji)")
                    // more content
                }
            } compactLeading: {
                Text("L")
            } compactTrailing: {
                Text("T \(context.state.emoji)")
            } minimal: {
                Text(context.state.emoji)
            }
            .widgetURL(URL(string: "http://www.apple.com"))
            .keylineTint(Color.red)
        }
    }
}

extension RefocusWidgetsAttributes {
    fileprivate static var preview: RefocusWidgetsAttributes {
        RefocusWidgetsAttributes(name: "World")
    }
}

extension RefocusWidgetsAttributes.ContentState {
    fileprivate static var smiley: RefocusWidgetsAttributes.ContentState {
        RefocusWidgetsAttributes.ContentState(emoji: "😀")
     }
     
     fileprivate static var starEyes: RefocusWidgetsAttributes.ContentState {
         RefocusWidgetsAttributes.ContentState(emoji: "🤩")
     }
}

#Preview("Notification", as: .content, using: RefocusWidgetsAttributes.preview) {
   RefocusWidgetsLiveActivity()
} contentStates: {
    RefocusWidgetsAttributes.ContentState.smiley
    RefocusWidgetsAttributes.ContentState.starEyes
}
