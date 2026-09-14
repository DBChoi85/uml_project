# Supported Mermaid-like Syntax

본 프로젝트는 Mermaid 전체 문법이 아니라 필요한 subset을 지원한다.

## Flowchart

```text
flowchart LR
A[Rectangle] --> B(Rounded)
B --> C{Decision}
C -->|Yes| D[Done]
A --- E[Association-like line]
```

### 방향

- `LR`
- `RL`
- `TB`
- `TD` (`TB`와 동일한 Top-Down 의미)
- `BT`

### 노드

- `A[Text]` — Rectangle
- `A(Text)` — Rounded Rectangle
- `A{Text}` — Diamond

### 관계

- `A --> B` — Flow Arrow
- `A -->|label| B` — Labelled Flow Arrow
- `A --- B` — Association

## Class Diagram

```text
classDiagram
direction LR

class User {
  -String id
  +login()
}

class Admin
User <|-- Admin : inherits
```

### 클래스 멤버

클래스 블록의 멤버는 Attribute와 Method로 분리한다. 현재는 괄호가 포함된 멤버를 Method로 취급한다.

### 관계

- `A -- B` — Association
- `A --> B` — Directed Association
- `A ..> B` — Dependency
- `A <|-- B` — Inheritance / Generalization
- `A ..|> B` — Realization
- `A o-- B` — Aggregation
- `A *-- B` — Composition

일부 역방향 표현도 지원한다.

## 미지원 또는 제한

- Mermaid 전체 문법
- namespace / note / annotation
- generic type의 완전한 표현
- multiplicity 별도 렌더링
- GUI 편집 결과의 Mermaid 역변환
