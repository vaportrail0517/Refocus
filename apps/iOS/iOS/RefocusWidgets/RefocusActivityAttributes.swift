//
//  RefocusActivityAttributes.swift
//  iOS
//
//  Created by Takechiyo Sakazaki on 2026/01/07.
//

import ActivityKit
import Foundation

struct RefocusAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        var dummy: Bool = false
    }

    var sessionStart: Date
}
