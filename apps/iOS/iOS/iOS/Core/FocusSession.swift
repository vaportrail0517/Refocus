//
//  FocusSession.swift
//  iOS
//
//  Created by Takechiyo Sakazaki on 2026/01/07.
//

import Foundation

struct FocusSession: Codable, Equatable {
    var startAt: Date
    var endAt: Date?

    var isRunning: Bool { endAt == nil }

    var elapsed: TimeInterval {
        (endAt ?? Date()).timeIntervalSince(startAt)
    }
}
