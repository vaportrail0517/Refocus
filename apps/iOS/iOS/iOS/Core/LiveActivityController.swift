//
//  LiveActivityController.swift
//  iOS
//
//  Created by Takechiyo Sakazaki on 2026/01/07.
//
import Foundation
import ActivityKit

enum LiveActivityController {
    static func start(startDate: Date) {
        let attributes = RefocusAttributes(sessionStart: startDate)
        let state = RefocusAttributes.ContentState(dummy: false)

        do {
            _ = try Activity.request(
                attributes: attributes,
                content: .init(state: state, staleDate: nil)
            )
        } catch {
            print("Live Activity start failed:", error)
        }
    }

    static func stopAll() {
        Task {
            for activity in Activity<RefocusAttributes>.activities {
                await activity.end(nil, dismissalPolicy: .immediate)
            }
        }
    }
}
