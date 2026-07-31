package bannerplugin

import (
	"bytes"
	"context"
	"fmt"
	"net/http"
	"strings"
)

// Config defines the plugin configuration.
type Config struct {
	HeaderName string `json:"headerName,omitempty"`
}

// CreateConfig creates the default plugin configuration.
func CreateConfig() *Config {
	return &Config{
		HeaderName: "X-Auth-Banner-Text",
	}
}

// Banner is the plugin middleware handler.
type Banner struct {
	next       http.Handler
	headerName string
	name       string
}

// New creates a new Banner plugin.
func New(ctx context.Context, next http.Handler, config *Config, name string) (http.Handler, error) {
	return &Banner{
		next:       next,
		headerName: config.HeaderName,
		name:       name,
	}, nil
}

func (b *Banner) ServeHTTP(rw http.ResponseWriter, req *http.Request) {
	bannerText := req.Header.Get(b.headerName)

	// If there's no auth header, skip injection and just pass the request through.
	if bannerText == "" {
		b.next.ServeHTTP(rw, req)
		return
	}

	recorder := &responseRecorder{
		ResponseWriter: rw,
		body:           bytes.NewBuffer(nil),
		statusCode:     http.StatusOK,
	}

	b.next.ServeHTTP(recorder, req)

	// Only inject HTML into text/html responses
	if strings.Contains(recorder.Header().Get("Content-Type"), "text/html") {
		html := recorder.body.String()

		injection := fmt.Sprintf(`<body><style>#root{position:absolute !important;top:40px !important;height:calc(100%% - 40px) !important;width:100%% !important;}#subscription-banner{position:fixed !important;top:0 !important;left:0 !important;right:0 !important;height:40px !important;background-color:#0f766e !important;color:#ffffff !important;font-family:sans-serif !important;font-size:14px !important;font-weight:500 !important;display:flex !important;align-items:center !important;justify-content:center !important;z-index:2147483647 !important;}</style><div id="subscription-banner">%s</div>`, bannerText)

		modified := strings.Replace(html, "<body>", injection, 1)

		rw.Header().Set("Content-Length", fmt.Sprint(len(modified)))
		rw.WriteHeader(recorder.statusCode)
		rw.Write([]byte(modified))
	} else {
		rw.WriteHeader(recorder.statusCode)
		rw.Write(recorder.body.Bytes())
	}
}

type responseRecorder struct {
	http.ResponseWriter
	body       *bytes.Buffer
	statusCode int
}

func (r *responseRecorder) WriteHeader(statusCode int) {
	r.statusCode = statusCode
}

func (r *responseRecorder) Write(b []byte) (int, error) {
	return r.body.Write(b)
}
