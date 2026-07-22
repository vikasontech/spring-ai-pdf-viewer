package org.radhe.spring_ai_pdf_viewer.controller

import kotlinx.coroutines.reactor.awaitSingle
import org.radhe.spring_ai_pdf_viewer.service.PdfMarkdownService
import org.radhe.spring_ai_pdf_viewer.service.PdfPasswordException
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.FilePart
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets

@RestController
class PdfController(
    private val pdfMarkdownService: PdfMarkdownService,
) {

    /**
     * Upload a PDF file and receive it back as a Markdown (.md) file.
     * The PDF text is extracted with Spring AI's PDF reader, then rewritten
     * as Markdown by a local Ollama model.
     *
     * For password-protected PDFs, pass the password as an additional form
     * field, e.g.:
     * curl -F "file=@doc.pdf" -F "password=secret" http://localhost:8080/api/pdf/convert -o doc.md
     */
    @PostMapping("/api/pdf/convert", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun convertPdfToMarkdown(
        @RequestPart("file") file: FilePart,
        @RequestPart(value = "password", required = false) password: String?,
    ): ResponseEntity<ByteArray> {
        val pdfBytes = DataBufferUtils.join(file.content())
            .map { buffer ->
                val bytes = ByteArray(buffer.readableByteCount())
                buffer.read(bytes)
                DataBufferUtils.release(buffer)
                bytes
            }
            .awaitSingle()

        val extractedText = pdfMarkdownService.extractText(pdfBytes, password)
        val markdown = pdfMarkdownService.convertToMarkdown(extractedText)

        val originalName = file.filename().ifBlank { "document.pdf" }
        val baseName = originalName.substringBeforeLast(".", originalName)
        val mdFilename = "$baseName.md"

        val headers = HttpHeaders().apply {
            contentDisposition = ContentDisposition.attachment()
                .filename(mdFilename, StandardCharsets.UTF_8)
                .build()
            contentType = MediaType.parseMediaType("text/markdown")
        }

        return ResponseEntity.ok()
            .headers(headers)
            .body(markdown.toByteArray(StandardCharsets.UTF_8))
    }

    @ExceptionHandler(PdfPasswordException::class)
    fun handlePdfPasswordException(ex: PdfPasswordException): ResponseEntity<String> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.message)
}
