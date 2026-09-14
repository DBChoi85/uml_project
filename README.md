# UML Project — Mermaid Editable Diagram Editor

Java Swing/AWT 기반의 스탠드얼론 다이어그램 편집기입니다. Mermaid-like 텍스트를 자동으로 다이어그램으로 생성한 뒤, UMLet과 유사하게 각 객체와 관계를 직접 편집할 수 있습니다.

## 주요 기능

- Flowchart 및 Class Diagram 파싱
- Mermaid-like 코드에서 자동 다이어그램 생성
- LR / RL / TB / TD / BT 레이아웃
- 노드/클래스 드래그 이동 및 8방향 리사이즈
- 빈 캔버스 드래그 Pan
- 마우스 휠 Zoom, Fit, 100% 보기
- 객체/클래스 텍스트 및 폰트 크기 편집
- Class name / Attributes / Methods 편집
- 직선/직교(orthogonal) 간선
- Association, Dependency, Inheritance, Realization, Aggregation, Composition 등 UML 관계
- 간선 라벨/폰트 크기/시작·끝 객체 편집
- PNG/JPG Export

## 요구 환경

- macOS에서 개발 및 확인
- JDK 17 이상
- 외부 라이브러리 없음

JDK 설치 예시(Homebrew):

```bash
brew install openjdk@21
```

## 실행

```bash
chmod +x run.sh test.sh
./run.sh
```

자체 테스트:

```bash
./test.sh
```

## Flowchart 예제

```text
flowchart LR
A[Start] --> B[Login]
B --> C{Valid?}
C -->|Yes| D[Main]
C -->|No| E[Error]
```

지원 방향: `LR`, `RL`, `TB`, `TD`, `BT`

## Class Diagram 예제

```text
classDiagram
direction LR

class User {
  -String id
  -String name
  +login()
  +logout()
}

class Admin {
  +manageUsers()
}

class Session
class Auditable

User <|-- Admin : inherits
User *-- Session : owns
Admin ..|> Auditable : implements
```

현재 지원하는 주요 관계:

| Mermaid-like | 의미 |
|---|---|
| `A -- B` | Association |
| `A --> B` | Directed Association |
| `A ..> B` | Dependency |
| `A <\|-- B` | Inheritance / Generalization |
| `A ..\|> B` | Realization |
| `A o-- B` | Aggregation |
| `A *-- B` | Composition |

## 화면 조작

- **노드/클래스 드래그**: 객체 이동
- **선택 객체의 핸들 드래그**: 크기 변경
- **빈 공간 드래그**: 전체 화면 Pan
- **마우스 휠**: 포인터 중심 Zoom
- **Fit**: 전체 다이어그램 맞춤
- **100%**: 기본 배율
- **Layout + Relayout**: 방향을 선택하여 자동 재배치

Pan은 뷰포트만 이동하며 객체의 실제 좌표나 PNG/JPG Export 결과에는 영향을 주지 않습니다.

## 프로젝트 구조

```text
uml_project/
├── src/diagrameditor/
│   ├── Main.java
│   ├── export/
│   ├── model/
│   ├── parser/
│   └── ui/
├── examples/
├── docs/
├── run.sh
├── test.sh
├── CHANGELOG.md
└── .github/workflows/build.yml
```

내부 처리 흐름:

```text
Mermaid-like Text
        ↓
   MermaidParser
        ↓
   DiagramModel
   ├─ DiagramNode
   └─ DiagramEdge
        ↓
   DiagramCanvas
   ├─ direct manipulation
   ├─ pan / zoom / resize
   └─ orthogonal routing
        ↓
      PNG / JPG
```

## 예제

- `examples/flowchart_large.mmd` — 큰 Flowchart
- `examples/class_diagram.mmd` — Class Diagram
- `examples/editor_architecture_class.mmd` — 현재 애플리케이션 구조 Class Diagram

애플리케이션의 `App Class` 버튼으로도 현재 구조 예제를 바로 불러올 수 있습니다.

## 현재 한계

- Mermaid 전체 문법이 아니라 프로젝트에서 정의한 subset을 지원합니다.
- Class annotation, namespace, generic type, note 등은 아직 지원하지 않습니다.
- multiplicity 문자열은 현재 별도 렌더링하지 않습니다.
- 직교 간선은 obstacle avoidance를 하지 않습니다.
- 간선 경유점 수동 편집은 지원하지 않습니다.
- GUI에서 수정한 결과를 Mermaid 원문으로 역변환하지 않습니다.

자세한 구조와 지원 문법은 `docs/`를 참고하십시오.
