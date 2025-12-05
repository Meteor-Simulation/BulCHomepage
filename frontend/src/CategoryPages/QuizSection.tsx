import React, { useState } from 'react';
import './QuizSection.css';

interface QuizQuestion {
  scenario: string;
  question: string;
  options: string[];
  correctIndex: number;
  explanation: string;
}

const quizData: QuizQuestion[] = [
  {
    scenario: 'SCENARIO 01',
    question: '사무실에서 업무 중 화재 경보가 울렸습니다. 복도로 나가보니 연기가 자욱합니다. 가장 먼저 해야 할 행동은?',
    options: [
      'A. 젖은 손수건을 찾으러 화장실로 뛴다',
      'B. 벽을 짚고 자세를 낮춰 비상구 방향으로 이동한다',
      'C. 엘리베이터 버튼을 누른다'
    ],
    correctIndex: 1,
    explanation: '연기가 찼을 때는 시야 확보가 어렵기 때문에 벽을 짚고 이동하는 것이 중요합니다. 젖은 손수건을 찾으러 가는 시간조차 위험할 수 있습니다.'
  },
  {
    scenario: 'SCENARIO 02',
    question: '화재로 인해 방 안에 갇혔습니다. 문 손잡이를 만져보니 뜨겁습니다. 어떻게 해야 할까요?',
    options: [
      'A. 문을 열고 빠르게 대피한다',
      'B. 문을 열지 않고 창문으로 구조 요청한다',
      'C. 문 아래 틈으로 상황을 확인한다'
    ],
    correctIndex: 1,
    explanation: '문 손잡이가 뜨겁다면 반대편에 불이 있다는 신호입니다. 문을 열면 역화(Backdraft)가 발생할 수 있어 매우 위험합니다.'
  },
  {
    scenario: 'SCENARIO 03',
    question: '대피 중 옷에 불이 붙었습니다. 가장 적절한 대응은?',
    options: [
      'A. 뛰어서 불을 끈다',
      'B. 멈추고, 엎드리고, 굴러서 불을 끈다',
      'C. 손으로 불을 두드려 끈다'
    ],
    correctIndex: 1,
    explanation: '"Stop, Drop, Roll" - 멈추고, 엎드리고, 굴러야 합니다. 뛰면 바람이 불어 불이 더 커지고, 손으로 끄면 화상을 입습니다.'
  }
];

const QuizSection: React.FC = () => {
  const [currentQuestion, setCurrentQuestion] = useState(0);
  const [showResult, setShowResult] = useState(false);
  const [isCorrect, setIsCorrect] = useState(false);
  const [quizCompleted, setQuizCompleted] = useState(false);
  const [score, setScore] = useState(0);

  const handleAnswer = (selectedIndex: number) => {
    const correct = selectedIndex === quizData[currentQuestion].correctIndex;
    setIsCorrect(correct);
    if (correct) {
      setScore(prev => prev + 1);
    }
    setShowResult(true);
  };

  const handleNextQuestion = () => {
    if (currentQuestion < quizData.length - 1) {
      setCurrentQuestion(prev => prev + 1);
      setShowResult(false);
    } else {
      setQuizCompleted(true);
    }
  };

  const resetQuiz = () => {
    setCurrentQuestion(0);
    setShowResult(false);
    setIsCorrect(false);
    setQuizCompleted(false);
    setScore(0);
  };

  const current = quizData[currentQuestion];

  return (
    <section className="quiz-section vr-section">
      <div className="quiz-container">
        <div className="quiz-grid">
          {/* Left Content */}
          <div className="quiz-left">
            <span className="quiz-badge">Self Assessment</span>
            <h2 className="quiz-title">당신의 안전 IQ는?</h2>
            <p className="quiz-description">
              화재 상황에서의 판단은 0.1초 만에 이루어집니다.<br />
              간단한 테스트를 통해 당신의 대처 능력을 확인해보세요.<br />
              대부분의 사람들은 이 테스트에서 <span className="text-highlight-underline">실수</span>를 합니다.
            </p>

            <div className="quiz-info-box">
              <h3 className="info-box-title">
                <svg className="info-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z" fill="currentColor"/>
                </svg>
                왜 교육이 필요할까요?
              </h3>
              <ul className="info-list">
                <li className="info-item">
                  <svg className="check-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path d="M22 11.08V12a10 10 0 11-5.93-9.14" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                    <path d="M22 4L12 14.01l-3-3" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                  <p><strong>무의식적 반응(Muscle Memory)</strong>을 훈련하지 않으면 패닉 상태에서 몸이 굳어버립니다.</p>
                </li>
                <li className="info-item">
                  <svg className="check-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path d="M22 11.08V12a10 10 0 11-5.93-9.14" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                    <path d="M22 4L12 14.01l-3-3" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                  <p>이론으로 배운 "낮은 자세"는 실제 연기 속에서 잊혀지기 쉽습니다. VR은 이를 <strong>경험</strong>으로 각인시킵니다.</p>
                </li>
              </ul>
            </div>
          </div>

          {/* Right Content - Quiz Widget */}
          <div className="quiz-widget">
            <div className="quiz-widget-bg-icon">
              <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M12 22C17.5228 22 22 17.5228 22 12C22 6.47715 17.5228 2 12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22Z" stroke="currentColor" strokeWidth="2"/>
                <path d="M9 9C9 7.34315 10.3431 6 12 6C13.6569 6 15 7.34315 15 9C15 10.6569 13.6569 12 12 12V14" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
                <circle cx="12" cy="18" r="1" fill="currentColor"/>
              </svg>
            </div>

            {!quizCompleted ? (
              <>
                {/* Question View */}
                {!showResult ? (
                  <div className="quiz-question-container">
                    <div className="quiz-header">
                      <span className="quiz-scenario">{current.scenario}</span>
                      <span className="quiz-progress">{currentQuestion + 1} / {quizData.length}</span>
                    </div>
                    <h3 className="quiz-question">{current.question}</h3>
                    <div className="quiz-options">
                      {current.options.map((option, index) => (
                        <button
                          key={index}
                          className="quiz-option"
                          onClick={() => handleAnswer(index)}
                        >
                          <span>{option}</span>
                          <svg className="option-arrow" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <path d="M5 12H19M19 12L12 5M19 12L12 19" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                          </svg>
                        </button>
                      ))}
                    </div>
                  </div>
                ) : (
                  /* Result View */
                  <div className="quiz-result-container">
                    <div className={`result-icon ${isCorrect ? 'correct' : 'incorrect'}`}>
                      {isCorrect ? (
                        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                          <path d="M20 6L9 17L4 12" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"/>
                        </svg>
                      ) : (
                        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                          <path d="M18 6L6 18M6 6L18 18" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"/>
                        </svg>
                      )}
                    </div>
                    <h3 className="result-title">{isCorrect ? '정답입니다!' : '아쉽네요!'}</h3>
                    <p className="result-explanation">{current.explanation}</p>
                    <button className="quiz-next-btn" onClick={handleNextQuestion}>
                      {currentQuestion < quizData.length - 1 ? '다음 문제' : '결과 보기'}
                    </button>
                  </div>
                )}
              </>
            ) : (
              /* Final Result */
              <div className="quiz-final-result">
                <div className={`final-score-circle ${score === quizData.length ? 'perfect' : score >= quizData.length / 2 ? 'good' : 'needs-work'}`}>
                  <span className="final-score">{score}/{quizData.length}</span>
                </div>
                <h3 className="final-title">
                  {score === quizData.length ? '완벽합니다!' : score >= quizData.length / 2 ? '좋아요!' : '더 연습이 필요해요'}
                </h3>
                <p className="final-message">
                  {score === quizData.length
                    ? '화재 안전에 대한 지식이 뛰어납니다. 하지만 실제 상황에서의 대응력은 VR 훈련으로 더욱 강화할 수 있습니다.'
                    : '실제 화재 상황에서는 더 빠른 판단이 필요합니다. VR 시뮬레이션으로 몸이 기억하는 훈련을 시작해보세요.'}
                </p>
                <button className="quiz-retry-btn" onClick={resetQuiz}>
                  다시 도전하기
                </button>
              </div>
            )}
          </div>
        </div>
      </div>
    </section>
  );
};

export default QuizSection;
