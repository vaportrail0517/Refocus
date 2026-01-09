//
//  RefocusWidgetsBundle.swift
//  RefocusWidgets
//
//  Created by Takechiyo Sakazaki on 2025/12/19.
//

import WidgetKit
import SwiftUI

@main
struct RefocusWidgetsBundle: WidgetBundle {
    var body: some Widget {
        RefocusLiveActivityWidget()   // ← タイマー表示のほうを登録
        // RefocusWidgetsLiveActivity() // ← テンプレは一旦外す（混乱の元）
    }
}

