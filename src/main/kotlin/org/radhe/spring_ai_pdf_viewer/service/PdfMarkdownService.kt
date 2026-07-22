package org.radhe.spring_ai_pdf_viewer.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.reader.pdf.PagePdfDocumentReader
import org.springframework.core.io.ByteArrayResource
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream

/**
 * Thrown when a PDF is encrypted and no password (or an incorrect password)
 * was supplied to unlock it.
 */
class PdfPasswordException(message: String) : RuntimeException(message)

/**
 * Extracts text from a PDF (via Spring AI's PagePdfDocumentReader) and asks a
 * local Ollama model to re-express that content as a Markdown document,
 * preserving the original data rather than summarizing it away.
 */
@Service
class PdfMarkdownService(
    chatClientBuilder: ChatClient.Builder,
) {

    private val chatClient: ChatClient = chatClientBuilder.build()

    suspend fun extractText(pdfBytes: ByteArray, password: String? = null): String = withContext(Dispatchers.IO) {
        val resource = ByteArrayResource(decryptIfNeeded(pdfBytes, password))
        val reader = PagePdfDocumentReader(resource)
        reader.get().joinToString(separator = "\n\n") { it.text.orEmpty() }
    }

    /**
     * If the PDF is password-protected, unlocks it with the supplied password
     * and returns a decrypted copy of its bytes so downstream readers don't
     * need to know about the password. Non-encrypted PDFs pass through as-is.
     */
    private fun decryptIfNeeded(pdfBytes: ByteArray, password: String?): ByteArray {
        val document = try {
            Loader.loadPDF(pdfBytes, password.orEmpty())
        } catch (e: InvalidPasswordException) {
            val message = if (password.isNullOrBlank()) {
                "This PDF is password-protected. Supply a password using the 'password' form field."
            } else {
                "The password supplied for this PDF is incorrect."
            }
            throw PdfPasswordException(message)
        }

        return document.use { doc ->
            if (doc.isEncrypted) {
                doc.setAllSecurityToBeRemoved(true)
            }
            ByteArrayOutputStream().also { out -> doc.save(out) }.toByteArray()
        }
    }

    suspend fun convertToMarkdown(extractedText: String): String = withContext(Dispatchers.IO) {
        val prompt = """
            Convert the following text, extracted from a PDF, into a well-structured
            Markdown document. Preserve all of the original data, facts, numbers and
            wording. Do not summarize, omit or invent information. Use Markdown headings,
            lists and tables where the structure of the source suggests it. Respond with
            only the Markdown content, no extra commentary.

            PDF content:
            ---
            $extractedText
            ---
        """.trimIndent()

        chatClient.prompt()
            .user(prompt)
            .call()
            .content()
            .orEmpty()
    }
}
