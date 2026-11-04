# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: pvb.spec.js >> PvB workflow (guest vs bot) >> create game with Fairy Stockfish, play 2 moves, game is listed in my bot games
- Location: tests/pvb.spec.js:37:9

# Error details

```
Test timeout of 60000ms exceeded.
```

# Page snapshot

```yaml
- generic [active] [ref=f2e1]:
  - generic [ref=f2e3]:
    - link [ref=f2e5] [cursor=pointer]:
      - /url: https://discord.gg/WEGDqnWXNg
      - img "join our Discord" [ref=f2e6]
    - link [ref=f2e7] [cursor=pointer]:
      - /url: /
      - img "lobby" [ref=f2e8]
    - img "play bot" [ref=f2e10] [cursor=pointer]
    - img "play bot" [ref=f2e12] [cursor=pointer]
    - link [ref=f2e13] [cursor=pointer]:
      - /url: /puzzles
      - img "puzzles" [ref=f2e14]
    - link [ref=f2e15] [cursor=pointer]:
      - /url: /database
      - img "database" [ref=f2e16]
    - link [ref=f2e17] [cursor=pointer]:
      - /url: /analysis
      - img "analysis board" [ref=f2e18]
    - link [ref=f2e19] [cursor=pointer]:
      - /url: /global
      - img "global" [ref=f2e20]
    - link [ref=f2e21] [cursor=pointer]:
      - /url: https://elephanchessio.myspreadshop.com/all
      - img "merch" [ref=f2e22]
    - link [ref=f2e23] [cursor=pointer]:
      - /url: https://ko-fi.com/elephantchess
      - img "support" [ref=f2e24]
    - link [ref=f2e25] [cursor=pointer]:
      - /url: /about
      - img "about" [ref=f2e26]
    - generic [ref=f2e27]:
      - img "turn to play" [ref=f2e30] [cursor=pointer]
      - img "menu" [ref=f2e33]
  - generic [ref=f2e34]:
    - generic [ref=f2e35]:
      - table [ref=f2e37]:
        - rowgroup [ref=f2e38]:
          - row [ref=f2e39]:
            - cell [ref=f2e40]:
              - img "training" [ref=f2e41]
            - cell [ref=f2e42]:
              - table [ref=f2e43]:
                - rowgroup [ref=f2e44]:
                  - row [ref=f2e45]:
                    - cell "Player" [ref=f2e46]
                    - 'cell "guest #uxw8ueEH" [ref=f2e47]'
                  - row [ref=f2e50]:
                    - cell "Color" [ref=f2e51]
                    - cell "Red" [ref=f2e52]
                  - row [ref=f2e54]:
                    - cell "Created" [ref=f2e55]
                    - cell "Wed. 4 Nov. 12:19" [ref=f2e56]
                  - row [ref=f2e57]:
                    - cell "Engine" [ref=f2e58]
                    - cell "Fairy Stockfish" [ref=f2e59]
                  - row [ref=f2e60]:
                    - cell "Depth" [ref=f2e61]
                    - cell "1" [ref=f2e62]
                  - row [ref=f2e63]:
                    - cell "Outcome" [ref=f2e64]
                    - cell "--" [ref=f2e65]
      - table [ref=f2e67]:
        - rowgroup [ref=f2e68]:
          - row [ref=f2e69] [cursor=pointer]:
            - cell [ref=f2e70]:
              - img "resign" [ref=f2e71]
            - cell "Resign" [ref=f2e72]
          - row [ref=f2e73] [cursor=pointer]:
            - cell [ref=f2e74]:
              - img "cancel" [ref=f2e75]
            - cell "Cancel" [ref=f2e76]
      - generic [ref=f2e77]:
        - link [ref=f2e79] [cursor=pointer]:
          - /url: https://ko-fi.com/elephantchess
          - img "support us on Ko-fi" [ref=f2e80]
        - link "Why Support the Project" [ref=f2e82] [cursor=pointer]:
          - /url: /about/faq#why-support-us
      - button "Analyze" [ref=f2e84]
    - generic [ref=f2e86]:
      - generic [ref=f2e87]:
        - generic [ref=f2e88]: "1"
        - generic [ref=f2e93]: "2"
        - generic [ref=f2e98]: "3"
        - generic [ref=f2e103]: "4"
        - generic [ref=f2e110]: "5"
        - generic [ref=f2e117]: "6"
        - generic [ref=f2e122]: "7"
        - generic [ref=f2e127]: "8"
        - generic [ref=f2e132]: "9"
      - generic [ref=f2e347]:
        - generic [ref=f2e348]: 九
        - generic [ref=f2e352]: 八
        - generic [ref=f2e356]: 七
        - generic [ref=f2e360]: 六
        - generic [ref=f2e364]: 五
        - generic [ref=f2e368]: 四
        - generic [ref=f2e372]: 三
        - generic [ref=f2e376]: 二
        - generic [ref=f2e380]: 一
    - generic [ref=f2e384]:
      - generic [ref=f2e385]:
        - generic [ref=f2e386]:
          - generic [ref=f2e387]:
            - img "Select 'Traditional' piece style" [ref=f2e389]
            - img "Select 'Romanized Rounded' piece style" [ref=f2e391]
          - img "Flip board" [ref=f2e394]
        - link "Open advanced settings" [ref=f2e395] [cursor=pointer]:
          - /url: "#"
          - text: advanced
      - table [ref=f2e398]:
        - row [ref=f2e399]:
          - cell [ref=f2e400]:
            - img "Navigate to the beginning" [ref=f2e402]
            - img "Navigate to previous" [ref=f2e404]
          - cell [ref=f2e405]:
            - link [ref=f2e407] [cursor=pointer]:
              - /url: "#"
          - cell [ref=f2e409]:
            - img "Navigate to the end" [ref=f2e411]
            - img "Navigate to previous" [ref=f2e413]
  - generic [ref=f2e417]:
    - paragraph [ref=f2e418]:
      - text: You are currently logged in as
      - generic [ref=f2e419]: "guest #uxw8ueEH"
      - text: . This is a 30 days temporary session attached to your device. To be able to authenticate across devices and enjoy all the features, you can sign up to create an account or log in if you already have one.
    - paragraph [ref=f2e420]:
      - text: The website is totally free to use and sign up is quick. Read more on the
      - link "FAQ" [ref=f2e421] [cursor=pointer]:
        - /url: /about/faq#why-sign-up
      - text: .
    - paragraph [ref=f2e422]: You have the possibility to transfer the games played as guest on sign up.
    - paragraph [ref=f2e423]:
      - text: If you forgot your password, you can
      - link "reset it" [ref=f2e424] [cursor=pointer]:
        - /url: /recovery
      - text: .
    - paragraph [ref=f2e425]: This session expires on Fri. 4 Dec. 2026 12:19.
```