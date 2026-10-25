import React, { useEffect, useRef, useState } from "react";
import { apiRequest, jsonBody } from "../../api.js";
import "./AiAssistantPage.css";

const welcomeMessage = {
  id: "welcome",
  role: "assistant",
  content: "Hello! I can help with study ideas, explanations, writing, and everyday questions. What would you like to explore?",
  isWelcome: true,
};

const suggestedQuestions = [
  "Explain photosynthesis in simple words",
  "Help me build a weekly study plan",
  "Give me a fun classroom activity",
];

function SparkleIcon({ className = "" }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="m12 2 1.9 6.1L20 10l-6.1 1.9L12 18l-1.9-6.1L4 10l6.1-1.9L12 2Z" />
      <path d="m19 15 .9 2.1L22 18l-2.1.9L19 21l-.9-2.1L16 18l2.1-.9L19 15Z" />
    </svg>
  );
}

export default function AiAssistantPage({ credentials }) {
  const [configured, setConfigured] = useState(null);
  const [statusError, setStatusError] = useState("");
  const [messages, setMessages] = useState([welcomeMessage]);
  const [draft, setDraft] = useState("");
  const [isSending, setIsSending] = useState(false);
  const [error, setError] = useState("");
  const endOfMessagesRef = useRef(null);
  const inputRef = useRef(null);

  useEffect(() => {
    let cancelled = false;
    apiRequest("/api/assistant/status", credentials)
      .then((status) => {
        if (!cancelled) setConfigured(status.configured);
      })
      .catch((requestError) => {
        if (!cancelled) setStatusError(requestError.message);
      });
    return () => {
      cancelled = true;
    };
  }, [credentials]);

  useEffect(() => {
    endOfMessagesRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [messages, isSending]);

  async function sendMessage(event) {
    event.preventDefault();
    const content = draft.trim();
    if (!content || isSending || !configured) return;

    const userMessage = { id: crypto.randomUUID(), role: "user", content };
    const conversation = [
      ...messages.filter((message) => !message.isWelcome),
      userMessage,
    ].slice(-12);
    setMessages((current) => [...current, userMessage]);
    setDraft("");
    setError("");
    setIsSending(true);

    try {
      const response = await apiRequest("/api/assistant/chat", credentials, {
        method: "POST",
        body: jsonBody({
          messages: conversation.map(({ role, content: messageContent }) => ({
            role,
            content: messageContent,
          })),
        }),
      });
      setMessages((current) => [
        ...current,
        { id: crypto.randomUUID(), role: "assistant", content: response.reply },
      ]);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setIsSending(false);
      inputRef.current?.focus();
    }
  }

  function startNewChat() {
    if (isSending) return;
    setMessages([welcomeMessage]);
    setDraft("");
    setError("");
    inputRef.current?.focus();
  }

  const ready = configured === true;

  return (
    <section className="assistant-page">
      <div className="assistant-hero">
        <div className="assistant-hero-copy">
          <span className="assistant-eyebrow"><SparkleIcon /> YOUR SCHOOL'S AI COMPANION</span>
          <h2>Curiosity starts<br />with a question.</h2>
          <p>Explore ideas, make learning plans, and get clear explanations in a friendly space.</p>
        </div>
        <div className="assistant-hero-orbit" aria-hidden="true">
          <span className="assistant-orbit-ring assistant-orbit-ring-one" />
          <span className="assistant-orbit-ring assistant-orbit-ring-two" />
          <span className="assistant-orbit-core"><SparkleIcon /></span>
          <span className="assistant-orbit-dot assistant-orbit-dot-one" />
          <span className="assistant-orbit-dot assistant-orbit-dot-two" />
        </div>
      </div>

      <div className="assistant-privacy" role="note">
        <span className="assistant-privacy-icon" aria-hidden="true">i</span>
        <p><strong>Privacy first.</strong> Don’t include student or family names, contact details, grades, attendance, or other private information. Chats aren’t saved by this app, but your question is sent to OpenAI to generate a response.</p>
      </div>

      <section className="assistant-chat-card" aria-label="AI chat">
        <header className="assistant-chat-header">
          <div className="assistant-chat-identity">
            <span className="assistant-avatar"><SparkleIcon /></span>
            <span><strong>School AI Assistant</strong><small>{ready ? "Ready to explore with you" : "General questions only"}</small></span>
          </div>
          <button type="button" className="assistant-new-chat" onClick={startNewChat} disabled={isSending}>
            New chat
          </button>
        </header>

        {(configured === false || statusError) && (
          <div className="assistant-setup" role="status">
            <strong>{statusError ? "Assistant status unavailable" : "AI assistant is not connected yet"}</strong>
            <span>{statusError || "Ask your administrator to configure the OpenAI API key on the server. The key is never stored in this browser."}</span>
          </div>
        )}

        <div className="assistant-messages" role="log" aria-label="Chat messages" aria-live="polite">
          {messages.map((message) => (
            <article className={`assistant-message assistant-message-${message.role}`} key={message.id}>
              {message.role === "assistant" && (
                <span className="assistant-message-avatar"><SparkleIcon /></span>
              )}
              <div className="assistant-bubble">
                {message.isWelcome && <span className="assistant-bubble-label">A warm welcome</span>}
                <p>{message.content}</p>
              </div>
            </article>
          ))}
          {isSending && (
            <div className="assistant-message assistant-message-assistant" role="status">
              <span className="assistant-message-avatar"><SparkleIcon /></span>
              <div className="assistant-bubble assistant-thinking">
                <span /><span /><span /><small>Thinking</small>
              </div>
            </div>
          )}
          <div ref={endOfMessagesRef} />
        </div>

        {messages.length === 1 && (
          <div className="assistant-suggestions" aria-label="Suggested questions">
            <span>TRY ASKING</span>
            <div>
              {suggestedQuestions.map((question) => (
                <button
                  type="button"
                  key={question}
                  onClick={() => {
                    setDraft(question);
                    inputRef.current?.focus();
                  }}
                  disabled={!ready}
                >
                  {question}
                </button>
              ))}
            </div>
          </div>
        )}

        {error && <p className="assistant-error" role="alert">{error}</p>}

        <form className="assistant-composer" onSubmit={sendMessage}>
          <label className="assistant-sr-only" htmlFor="assistant-question">Your question</label>
          <textarea
            id="assistant-question"
            ref={inputRef}
            value={draft}
            onChange={(event) => setDraft(event.target.value.slice(0, 2000))}
            onKeyDown={(event) => {
              if (event.key === "Enter" && !event.shiftKey) {
                event.preventDefault();
                event.currentTarget.form.requestSubmit();
              }
            }}
            placeholder={ready ? "Ask a question… (Shift + Enter for a new line)" : "The AI assistant will be ready after setup"}
            rows={2}
            maxLength={2000}
            disabled={!ready || isSending}
          />
          <button
            className="assistant-send"
            type="submit"
            aria-label="Send question"
            disabled={!ready || isSending || !draft.trim()}
          >
            <span>{isSending ? "Sending" : "Send"}</span>
            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M4 12h15m-6-6 6 6-6 6" />
            </svg>
          </button>
        </form>
        <p className="assistant-disclaimer">AI can make mistakes. Check important information with a trusted person.</p>
      </section>
    </section>
  );
}
