# NOTICE — 귀속 및 라이선스 고지

이 프로젝트의 보행자 동역학 **연산 모델 3종(CFSM · SFM · AVM)** 의 수식과 기본 파라미터는
**PedestrianDynamics** 의 오픈소스 라이브러리(`libsimulator`, **LGPL-3.0-or-later**)에서
추출하여 JavaScript로 포팅한 것이다.

- 소스: https://github.com/PedestrianDynamics/jupedsim
- 해당 부분 라이선스: GNU LGPL-3.0-or-later

포팅된 모델 구현(`app.js` 내 `cfsmCompute` / `sfmCompute` / `avmCompute` 및 관련 파라미터)은
원 소스의 파생물로서 LGPL-3.0의 조건을 따른다.

## 원 논문

- **CFSM** — A. Tordeux, M. Chraibi, A. Seyfried, *Collision-free speed model for pedestrian dynamics* (2016). arXiv:1512.05597
- **SFM** — D. Helbing, I. Farkas, T. Vicsek, *Simulating dynamical features of escape panic*, Nature (2000). doi:10.1038/35035023
- **AVM** — Q. Xu, M. Chraibi, A. Seyfried, *Anticipation in a velocity-based model for pedestrian dynamics*, Transportation Research Part C (2021). doi:10.1016/j.trc.2021.103464

## BR(Bae–Ryou) 모델 — 한국 유홍선(중앙대) 그룹

`brCompute`의 연기력(외부/내부)·기반식은 아래 논문의 식을 따랐고, 복사력은 원 논문([1])의
상수식이 비공개라 점원(point-source) 열유속 모델 `q″ = χ_r·Q̇/(4πr²)` 로 개념을 재현했다.

- [1] S. Bae, H.S. Ryou, *A Mathematical Modeling of the Interaction Between Evacuees and Fire Through Radiation*, Fire Technology 52, 847–864 (2016). doi:10.1007/s10694-015-0506-x
- [2] S. Bae, H.S. Ryou, *Development of a smoke effect model for representing the psychological pressure from the smoke*, Safety Science 77, 57–65 (2015).
- [3] S. Bae, J.-H. Choi, H.S. Ryou, *Modification of Interaction Forces between Smoke and Evacuees*, Energies 13(16):4177 (2020, open access). doi:10.3390/en13164177

## 예제 시나리오

갤러리의 예제 시나리오는 PedestrianDynamics 공식 노트북(https://www.jupedsim.org/stable/notebooks/)을
참고해 한글로 재구성했다.

## 그 외

- 톤앤매너 참고: 메테오시뮬레이션 / BULC (https://msimul.modoo.at/)
- 본 쇼케이스의 UI·시나리오 구성·문서는 학습/프로토타입 목적의 비공식 결과물이다.
