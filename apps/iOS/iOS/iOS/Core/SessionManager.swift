//
//  SessionManager.swift
//  iOS
//
//  Created by Takechiyo Sakazaki on 2026/01/07.
//
import Foundation
import Observation

@Observable
final class SessionManager {
    private(set) var session: FocusSession?

    func start() {
        guard session?.isRunning != true else { return }
        session = FocusSession(startAt: Date(), endAt: nil)
    }

    func stop() {
        guard var s = session, s.isRunning else { return }
        s.endAt = Date()
        session = s
    }
}

