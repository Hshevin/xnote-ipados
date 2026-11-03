package com.xnotes.pen

import android.content.ContextWrapper
import com.xnotes.storage.DocumentRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class DocumentRegistryTest {
    private fun database(block: (DocumentRegistry,File)->Unit) {
        val base=PenTestRunner.instance.targetContext
        val root=File(base.cacheDir,"registry-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context=object:ContextWrapper(base) { override fun getNoBackupFilesDir()=root }
        val db=DocumentRegistry(context)
        try { block(db,root) } finally { db.close(); root.deleteRecursively() }
    }
    @Test fun duplicateIdentityIsExplicitAndMoveKeepsIdentity() = database { db,_ ->
        val id=UUID.randomUUID().toString()
        db.register(id,"xnote","note","content://example/document/a")
        db.moved("content://example/document/a","content://example/document/b")
        assertEquals(id,db.idAt("content://example/document/b"))
        db.register(id,"xnote","external copy","content://example/document/c")
        try { db.resolve(id); fail("must report conflict") } catch(_:IllegalStateException) {}
        db.markUnavailable("content://example/document/c")
        assertEquals("content://example/document/b",db.resolve(id))
    }
    @Test fun uriAliasesDoNotBecomeFalseCopies() = database { db,_ ->
        val id=UUID.randomUUID().toString()
        db.register(id,"xnote","note","content://example/tree/root/document/a")
        db.register(id,"xnote","note","content://example/document/a")
        assertEquals(1,db.locations(id).size)
    }
    @Test fun moveReusesStaleDestinationAndAliasCanBeMarkedUnavailable() = database { db,_ ->
        val id = UUID.randomUUID().toString()
        val oldId = UUID.randomUUID().toString()
        db.register(id,"xnote","source","content://example/document/a")
        db.register(oldId,"xnote","old target","content://example/document/b")
        db.markUnavailable("content://example/tree/root/document/b")
        assertTrue(db.locations(oldId).isEmpty())
        db.moved("content://example/document/a","content://example/document/b")
        assertEquals(id,db.idAt("content://example/document/b"))
        assertEquals("content://example/document/b",db.resolve(id))
    }
    @Test fun referencesUndoAndActiveLeaseProtectOwnedResource() = database { db,root ->
        val file=File(root,"resource.pdf").apply { writeText("immutable") }
        val resource=DocumentRegistry.Resource("id","paper",file.path,file.length(),"digest")
        db.registerResource(resource)
        db.references("chat:one",setOf("id"))
        assertEquals(0,db.collect(Long.MAX_VALUE/2,0,root))
        db.references("undo:one",setOf("id")); db.references("chat:one",emptySet())
        assertEquals(0,db.collect(Long.MAX_VALUE/2,0,root))
        val lease=db.lease("id",1)
        db.references("undo:one",emptySet())
        assertEquals(0,db.collect(Long.MAX_VALUE/2,0,root))
        db.release(lease)
        assertEquals(1,db.collect(Long.MAX_VALUE/2,0,root)); assertFalse(file.exists())
    }
    @Test fun failedReferenceChangePreservesPreviousOwnerAndNeverDeletesExternalFiles() = database { db,root ->
        val file=File(root,"external.pdf").apply { writeText("original") }
        db.registerResource(DocumentRegistry.Resource("id","paper",file.path,file.length(),"digest",owned=false))
        db.references("note:one",setOf("id"))
        try { db.references("note:one",setOf("missing")); fail() } catch(_:IllegalStateException) {}
        assertEquals(setOf("id"),db.referenceIds("note:one"))
        db.references("note:one",emptySet())
        assertEquals(0,db.collect(Long.MAX_VALUE/2,0,root)); assertEquals("original",file.readText())
    }
}
