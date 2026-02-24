/*
 * どこで: Matchmaking ドメインモデル
 * 何を: サポートするキューモードを定義する
 * なぜ: mode 入力の妥当性を列挙型で固定するため
 */
package com.example.matchmaking.model;

public enum MatchMode {
  CASUAL("casual"),
  RANK("rank");

  private final String value;

  MatchMode(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  /**
   * 役割: API で受け取った mode 文字列を内部列挙型へ変換する。
   * 動作: 大文字小文字を無視して一致判定を行い、未対応値は IllegalArgumentException を送出する。
   * 前提: mode は null でないことを呼び出し側で保証する。
   */
  public static MatchMode fromValue(String mode) {
    for (MatchMode matchMode : values()) {
      if (matchMode.value.equalsIgnoreCase(mode)) {
        return matchMode;
      }
    }
    throw new IllegalArgumentException("unsupported mode: " + mode);
  }
}
