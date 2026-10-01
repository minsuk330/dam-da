// DESIGN.md에서 생성된 파일. 손으로 고치지 않는다 — DESIGN.md를 고치고 `npm run design:tokens`.
export const colors = {
  "canvas": "#F3F6FF",
  "surface": "#FFFFFF",
  "surfaceSoft": "#EEF2FF",
  "outline": "#E2E7FA",
  "ink": "#12141F",
  "inkSecondary": "#3D4A6B",
  "inkMuted": "#646B88",
  "primary": "#2E5BE8",
  "primaryInk": "#2448C8",
  "primaryPressed": "#2149CF",
  "onPrimary": "#FFFFFF",
  "primaryTint": "#D6E1FF",
  "lavender": "#BDCDFF",
  "lavenderDeep": "#A9BDFF",
  "field": "#DCE4FC",
  "fieldPlaceholder": "#59607A",
  "inverse": "#0B0C12",
  "onInverse": "#FFFFFF",
  "stage": "#D4D7DE",
  "success": "#16A06A",
  "successTint": "#D9F4E8",
  "successInk": "#0B6B46",
  "danger": "#E5484D",
  "dangerTint": "#FFE3E3",
  "dangerInk": "#A8242B",
  "warning": "#F0A500",
  "warningTint": "#FFF0CC",
  "warningInk": "#7A4D00",
  "warningFill": "#B87800",
  "glass": "#DCE4FCB8",
  "glassItem": "#CED6EB"
} as const;

export const spacing = {
  "2xs": 2,
  "xs": 4,
  "sm": 8,
  "md": 12,
  "lg": 16,
  "xl": 20,
  "2xl": 24,
  "3xl": 32
} as const;

export const radius = {
  "sm": 12,
  "md": 20,
  "lg": 28,
  "xl": 32,
  "full": 9999
} as const;

export const typography = {
  "display": {
    "fontFamily": "SUIT-Bold",
    "fontSize": 28,
    "lineHeight": 36
  },
  "title": {
    "fontFamily": "SUIT-Bold",
    "fontSize": 22,
    "lineHeight": 30
  },
  "question": {
    "fontFamily": "SUIT-SemiBold",
    "fontSize": 22,
    "lineHeight": 31
  },
  "headline": {
    "fontFamily": "SUIT-SemiBold",
    "fontSize": 17,
    "lineHeight": 24
  },
  "body": {
    "fontFamily": "SUIT-Regular",
    "fontSize": 16,
    "lineHeight": 25
  },
  "subhead": {
    "fontFamily": "SUIT-Medium",
    "fontSize": 14,
    "lineHeight": 20
  },
  "caption": {
    "fontFamily": "SUIT-Medium",
    "fontSize": 12,
    "lineHeight": 17
  },
  "stat": {
    "fontFamily": "SUIT-SemiBold",
    "fontSize": 26,
    "lineHeight": 29
  }
} as const;

export const components = {
  "screen": {
    "backgroundColor": "#F3F6FF",
    "textColor": "#12141F",
    "typography": {
      "fontFamily": "SUIT-Regular",
      "fontSize": 16,
      "lineHeight": 25
    }
  },
  "captionText": {
    "backgroundColor": "#FFFFFF",
    "textColor": "#646B88",
    "typography": {
      "fontFamily": "SUIT-Medium",
      "fontSize": 12,
      "lineHeight": 17
    }
  },
  "buttonPrimary": {
    "backgroundColor": "#2E5BE8",
    "textColor": "#FFFFFF",
    "typography": {
      "fontFamily": "SUIT-SemiBold",
      "fontSize": 17,
      "lineHeight": 24
    },
    "rounded": 9999,
    "height": 56
  },
  "buttonPrimaryPressed": {
    "backgroundColor": "#2149CF",
    "textColor": "#FFFFFF",
    "rounded": 9999
  },
  "buttonSecondary": {
    "backgroundColor": "#FFFFFF",
    "textColor": "#12141F",
    "typography": {
      "fontFamily": "SUIT-SemiBold",
      "fontSize": 17,
      "lineHeight": 24
    },
    "rounded": 9999,
    "height": 56
  },
  "iconButton": {
    "backgroundColor": "#FFFFFF",
    "textColor": "#12141F",
    "rounded": 9999,
    "size": 48
  },
  "iconButtonPrimary": {
    "backgroundColor": "#2E5BE8",
    "textColor": "#FFFFFF",
    "rounded": 9999,
    "size": 44
  },
  "iconSmall": {
    "size": 16
  },
  "icon": {
    "size": 20
  },
  "iconLarge": {
    "size": 24
  },
  "iconXl": {
    "size": 32
  },
  "iconCircle": {
    "backgroundColor": "#D6E1FF",
    "textColor": "#2448C8",
    "rounded": 9999,
    "size": 44
  },
  "doneMark": {
    "backgroundColor": "#2E5BE8",
    "textColor": "#FFFFFF",
    "rounded": 9999,
    "size": 72
  },
  "phoneStage": {
    "backgroundColor": "#D4D7DE"
  },
  "answerOption": {
    "backgroundColor": "#EEF2FF",
    "textColor": "#12141F",
    "typography": {
      "fontFamily": "SUIT-SemiBold",
      "fontSize": 17,
      "lineHeight": 24
    },
    "rounded": 9999,
    "height": 56
  },
  "answerOptionSelected": {
    "backgroundColor": "#BDCDFF",
    "textColor": "#12141F",
    "typography": {
      "fontFamily": "SUIT-SemiBold",
      "fontSize": 17,
      "lineHeight": 24
    },
    "rounded": 9999
  },
  "answerOptionOutline": {
    "backgroundColor": "#E2E7FA",
    "width": 1
  },
  "answerOptionCorrect": {
    "backgroundColor": "#D9F4E8",
    "textColor": "#0B6B46",
    "typography": {
      "fontFamily": "SUIT-SemiBold",
      "fontSize": 17,
      "lineHeight": 24
    },
    "rounded": 9999
  },
  "answerOptionWrong": {
    "backgroundColor": "#FFE3E3",
    "textColor": "#A8242B",
    "typography": {
      "fontFamily": "SUIT-SemiBold",
      "fontSize": 17,
      "lineHeight": 24
    },
    "rounded": 9999
  },
  "stateRingCorrect": {
    "backgroundColor": "#16A06A",
    "width": 2
  },
  "stateRingWrong": {
    "backgroundColor": "#E5484D",
    "width": 2
  },
  "feedbackCorrect": {
    "backgroundColor": "#D9F4E8",
    "textColor": "#0B6B46",
    "typography": {
      "fontFamily": "SUIT-Medium",
      "fontSize": 14,
      "lineHeight": 20
    },
    "rounded": 20,
    "padding": 16
  },
  "feedbackWrong": {
    "backgroundColor": "#FFE3E3",
    "textColor": "#A8242B",
    "typography": {
      "fontFamily": "SUIT-Medium",
      "fontSize": 14,
      "lineHeight": 20
    },
    "rounded": 20,
    "padding": 16
  },
  "chipWarning": {
    "backgroundColor": "#FFF0CC",
    "textColor": "#7A4D00",
    "typography": {
      "fontFamily": "SUIT-Medium",
      "fontSize": 12,
      "lineHeight": 17
    },
    "rounded": 9999
  },
  "warningMark": {
    "backgroundColor": "#F0A500",
    "size": 8
  },
  "chipStatus": {
    "backgroundColor": "#2E5BE8",
    "textColor": "#FFFFFF",
    "typography": {
      "fontFamily": "SUIT-Medium",
      "fontSize": 12,
      "lineHeight": 17
    },
    "rounded": 9999
  },
  "chipSoft": {
    "backgroundColor": "#D6E1FF",
    "textColor": "#2448C8",
    "typography": {
      "fontFamily": "SUIT-Medium",
      "fontSize": 12,
      "lineHeight": 17
    },
    "rounded": 9999
  },
  "card": {
    "backgroundColor": "#FFFFFF",
    "textColor": "#12141F",
    "rounded": 28,
    "padding": 20
  },
  "cardLavender": {
    "backgroundColor": "#A9BDFF",
    "textColor": "#3D4A6B",
    "rounded": 28,
    "padding": 20
  },
  "cardHero": {
    "backgroundColor": "#2E5BE8",
    "textColor": "#FFFFFF",
    "rounded": 32,
    "padding": 24,
    "height": 156
  },
  "cardHeroPressed": {
    "backgroundColor": "#2149CF",
    "textColor": "#FFFFFF",
    "rounded": 32
  },
  "cardStat": {
    "backgroundColor": "#FFFFFF",
    "textColor": "#12141F",
    "rounded": 28,
    "padding": 20,
    "height": 152
  },
  "cardPressed": {
    "backgroundColor": "#EEF2FF",
    "textColor": "#12141F",
    "rounded": 28
  },
  "field": {
    "backgroundColor": "#DCE4FC",
    "textColor": "#12141F",
    "typography": {
      "fontFamily": "SUIT-Regular",
      "fontSize": 16,
      "lineHeight": 25
    },
    "rounded": 20
  },
  "fieldPlaceholder": {
    "backgroundColor": "#DCE4FC",
    "textColor": "#59607A",
    "typography": {
      "fontFamily": "SUIT-Regular",
      "fontSize": 16,
      "lineHeight": 25
    }
  },
  "textAreaCompact": {
    "height": 96
  },
  "textArea": {
    "height": 160
  },
  "textAreaLarge": {
    "height": 220
  },
  "gauge": {
    "backgroundColor": "#D6E1FF",
    "textColor": "#2448C8",
    "typography": {
      "fontFamily": "SUIT-Medium",
      "fontSize": 12,
      "lineHeight": 17
    },
    "rounded": 9999,
    "height": 8
  },
  "gaugeLarge": {
    "backgroundColor": "#D6E1FF",
    "rounded": 9999,
    "height": 12
  },
  "gaugeFill": {
    "backgroundColor": "#2E5BE8",
    "rounded": 9999
  },
  "gaugeReview": {
    "backgroundColor": "#FFF0CC",
    "textColor": "#7A4D00",
    "typography": {
      "fontFamily": "SUIT-Medium",
      "fontSize": 12,
      "lineHeight": 17
    },
    "rounded": 9999
  },
  "gaugeReviewFill": {
    "backgroundColor": "#B87800",
    "rounded": 9999
  },
  "gaugeLow": {
    "backgroundColor": "#FFE3E3",
    "textColor": "#A8242B",
    "typography": {
      "fontFamily": "SUIT-Medium",
      "fontSize": 12,
      "lineHeight": 17
    },
    "rounded": 9999
  },
  "gaugeLowFill": {
    "backgroundColor": "#E5484D",
    "rounded": 9999
  },
  "gaugeUnchecked": {
    "backgroundColor": "#E2E7FA",
    "rounded": 9999
  },
  "tabBar": {
    "backgroundColor": "#DCE4FCB8",
    "rounded": 9999,
    "padding": 8
  },
  "tabItem": {
    "backgroundColor": "#CED6EB",
    "textColor": "#3D4A6B",
    "rounded": 9999,
    "size": 56
  },
  "tabActive": {
    "backgroundColor": "#0B0C12",
    "textColor": "#FFFFFF",
    "rounded": 9999,
    "size": 56
  }
} as const;

/** 로드해야 하는 폰트 family 이름 (assets/fonts/<이름>.otf). */
export const fontFamilies = ["SUIT-Bold","SUIT-Medium","SUIT-Regular","SUIT-SemiBold"] as const;
