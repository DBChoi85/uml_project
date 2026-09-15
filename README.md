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
- **각 객체의 면마다 5개 연결 포트(총 20개)**
- **간선 끝점을 마우스로 드래그해 연결 포트에 자석식 스냅**
- **직교 간선의 중앙 핸들을 드래그해 경로 위치 조절**
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
- **간선 선택**: 양 끝에 파란 핸들, 중앙 직교 구간에 주황 핸들 표시
- **파란 끝점 핸들 드래그**: 가까운 객체의 연결 포트로 이동하고 스냅
- **주황 중앙 핸들 드래그**: 직교 간선의 중앙 경로 위치 조정
- **Reset route**: 수동 포트 및 경로 위치를 자동 상태로 복원

### Magnetic connection ports

객체는 각 면에 5개의 연결 포트를 가집니다. 사각형/클래스 박스 기준으로 `Top / Right / Bottom / Left`에 각각 5개이며 총 20개입니다. Diamond는 네 변을 동일한 방식으로 5등분해 포트를 배치합니다.

간선 끝점을 드래그하는 동안 모든 객체의 포트가 표시되고, 포인터가 가까워지면 가장 가까운 포트가 강조됩니다. 마우스를 놓으면 해당 객체·면·포트 번호가 간선에 저장됩니다. 포트를 직접 지정하지 않은 간선은 두 객체의 위치를 기준으로 가장 가까운 포트를 자동 선택합니다.

Pan은 뷰포트만 이동하며 객체의 실제 좌표나 PNG/JPG Export 결과에는 영향을 주지 않습니다.

## 프로젝트 구조

```text
uml_project/
├── src/diagrameditor/
│   ├── Main.java
│   ├── export/
│   ├── model/
│   │   └── PortSide.java
│   ├── parser/
│   └── ui/
│       ├── MainFrame.java
│       └── DiagramCanvas.java
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
   ├─ magnetic ports
   └─ orthogonal route editing
        ↓
      PNG / JPG
```

## 현재 한계

- Mermaid 전체 문법이 아니라 프로젝트에서 정의한 subset을 지원합니다.
- Class annotation, namespace, generic type, note 등은 아직 지원하지 않습니다.
- multiplicity 문자열은 현재 별도 렌더링하지 않습니다.
- 직교 간선은 obstacle avoidance를 하지 않습니다.
- 간선의 중앙 직교 구간은 이동할 수 있지만 임의 개수의 waypoint를 추가하는 기능은 아직 없습니다.
- GUI에서 수정한 결과를 Mermaid 원문으로 역변환하지 않습니다.

자세한 구조와 지원 문법은 `docs/`를 참고하십시오.
