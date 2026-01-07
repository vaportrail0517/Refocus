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
        // まずは Live Activity だけに絞る（確実にビルド通す）
        RefocusWidgetsLiveActivity()

        // いったんコメントアウト
        // RefocusWidgets()
        // RefocusWidgetsControl()
    }
}
