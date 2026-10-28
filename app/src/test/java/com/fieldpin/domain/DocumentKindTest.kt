package com.fieldpin.domain

import com.fieldpin.domain.model.DocumentKind
import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentKindTest {
    @Test fun imagesAreDetectedByMime() = assertEquals(DocumentKind.IMAGE, DocumentKind.of("image/png", "plan.png"))

    @Test fun pdfByMimeOrExtension() {
        assertEquals(DocumentKind.PDF, DocumentKind.of("application/pdf", "permit"))
        assertEquals(DocumentKind.PDF, DocumentKind.of("application/octet-stream", "Permit.PDF"))
    }

    @Test fun textLikeFiles() {
        assertEquals(DocumentKind.TEXT, DocumentKind.of("text/plain", "notes.txt"))
        assertEquals(DocumentKind.TEXT, DocumentKind.of("application/json", "site.json"))
        assertEquals(DocumentKind.TEXT, DocumentKind.of("application/octet-stream", "readings.csv"))
    }

    @Test fun everythingElseIsOther() {
        assertEquals(DocumentKind.OTHER, DocumentKind.of("application/vnd.ms-excel", "budget.xls"))
        assertEquals(DocumentKind.OTHER, DocumentKind.of("application/octet-stream", "drawing.dwg"))
    }
}
