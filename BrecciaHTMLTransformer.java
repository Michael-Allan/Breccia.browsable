package Breccia.Web.imager;

import Breccia.parser.*;
import Breccia.XML.translator.BrecciaXCursor;
import java.io.OutputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import Java.Unhandled;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.*;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import static Breccia.parser.AssociativeReference.ReferentClause;
import static Breccia.parser.Typestamp.empty;
import static Breccia.parser.plain.Project.newSourceReader;
import static Breccia.Web.imager.Imaging.imageSimpleName;
import static Breccia.Web.imager.Project.logger;
import static Breccia.XML.translator.XStreamConstants.EMPTY;
import static java.nio.file.Files.createFile;
import static java.nio.file.Files.newOutputStream;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static Java.Nodes.successor;
import static Java.Nodes.successorAfter;
import static Java.StringBuilding.clear;
import static Java.StringBuilding.collapseWhitespace;
import static javax.xml.transform.OutputKeys.DOCTYPE_SYSTEM;
import static javax.xml.transform.OutputKeys.ENCODING;
import static javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION;


public final class BrecciaHTMLTransformer implements FileTransformer<ReusableCursor> {


    /** @see #sourceCursor
      * @see #sourceTranslator
      */
    public BrecciaHTMLTransformer( ReusableCursor sourceCursor, BrecciaXCursor sourceTranslator ) {
        this.sourceCursor = sourceCursor;
        this.sourceTranslator = sourceTranslator; }



    /** The source translator to use during calls to this transformer.
      * Between calls, it may be used for other purposes.
      */
    public final BrecciaXCursor sourceTranslator;



   // ━━━  F i l e   T r a n s f o r m e r  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━


    public @Override Markup formalReferenceAt( final ReusableCursor in ) throws ParseError {
        final ResourceIndicant iR; {
            FractumIndicant iF; {
                final ReferentClause cR; {
                    final AssociativeReference rA; {
                        rA = in.asAssociativeReference();
                        if( rA == null ) return null; }
                    cR = rA.referentClause();
                    if( cR == null ) return null; }
                final var iIR = cR.inferentialReferentIndicant();
                if( iIR == null ) { // Then `cR` itself directly contains any `iF`.
                    iF = cR.fractumIndicant();
                    if( iF.patternMatchers() == null ) return null; } /* Without a matcher, `iF`
                      indicates the resource as a whole, making it informal within the image. */
                else iF = iIR.fractumIndicant(); /* The `iIR` of `cR` alone contains any `iF`.  Whether
                  this `iF` includes a matcher is immaterial ∵ already `iIR` itself infers one. */
                if( iF == null ) return null; }
            iR = iF.resourceIndicant();
            if( iR == null ) return null; } /* The absence of `iR` implies that the indicated resource
              is the containing file, which is not an external resource as required by the API. */
        if( iR.qualifiers().contains( "non-fractal" )) return null; /* Fractal alone implies formal,
          non-fractal implying a resource whose content is opaque to this transformer and therefore
          indeterminate of image form. */
        return iR.reference(); } /* The resource of `iR` is formal ∵ the associative reference containing
          `iR`refers to a matcher of markup *in* the resource and ∴ will be imaged as a hyperlink whose
          form depends on the content of the resource.  In short, it is formal ∵ it informs the image. */



    public @Override ReusableCursor sourceCursor() { return sourceCursor; }



    public @Override void transform( final Path sourceFile, final Path imageDirectory )
          throws ParseError, TransformError {
        final Path imageFile = imageDirectory.resolve( imageSimpleName( sourceFile ));
        try {

          // Breccia text file → X-Breccia parse events → X-Breccia DOM
          // ──────────────────────────────────────────────────────────
            try( final Reader sourceReader = newSourceReader​( sourceFile )) {
                sourceCursor.markupSource( sourceReader );
                if( sourceCursor.state().typestamp() == empty ) {
                    logger.fine( () -> "Imaging empty source file: " + sourceFile );
                    createFile( imageFile ); // Special case, no content to transform.
                    return; }
                sourceTranslator.markupSource( sourceCursor );
                domOutput.setNode( null/*make a new document*/ );
                try { identityTransformer.transform( new StAXSource(sourceTranslator), domOutput ); }
                  // `StAXSource` is ‘not reusable’ according to its API.  How that could be is puzzling
                  // given that it’s a pure wrapper, but let’s humour it.
                catch( final TransformerException xT ) {
                    if( xT.getCause() instanceof XMLStreamException ) {
                        final XMLStreamException xS = (XMLStreamException)(xT.getCause());
                        throw (ParseError)(xS.getCause()); } /* So advertising that the location data
                          of `ParseError` is available for the exception, in case the caller wants it. */
                    throw xT; }}

          // X-Breccia DOM → XHTML DOM
          // ─────────────────────────
            final Document d = (Document)(domOutput.getNode());
            final Node fileFractum = d.removeChild( d.getFirstChild() );
            assert "FileFractum".equals( fileFractum.getLocalName() );
            if( d.hasChildNodes() ) throw new IllegalStateException(); // One alone was present.
            final Element html = d.createElementNS( "http://www.w3.org/1999/xhtml", "html" );
            d.appendChild( html );

          // head
          // ┈┈┈┈
            final Element documentHead = d.createElement( "head" );
            html.appendChild( documentHead );
            for( Node n = successor(fileFractum);  n != null;  n = successor(n) ) {
                if( !"Head".equals( n.getLocalName() )) continue;
                final String tF = fileTitle( n );
                if( tF != null ) {
                    final Element title = d.createElement( "title" );
                    title.appendChild( d.createTextNode( tF ));
                    documentHead.appendChild( title );
                    break; }}

          // body
          // ┈┈┈┈
            final Element documentBody = d.createElement( "body" );
            html.appendChild( documentBody );
            documentBody.appendChild( fileFractum );

          // XHTML DOM → XHTML text file
          // ───────────────────────────
            domInput.setNode( d );
            try( final OutputStream imageWriter = newOutputStream​( imageFile, CREATE_NEW )) {
                imageFileOutput.setOutputStream( imageWriter );
                identityTransformer.transform( domInput, imageFileOutput ); }}
        catch( IOException|TransformerException x ) {
            throw new TransformError( imageFile, "Unable to make image file", x ); }}



////  P r i v a t e  ////////////////////////////////////////////////////////////////////////////////////


    private final DOMSource domInput = new DOMSource();



    private final DOMResult domOutput = new DOMResult();



    /** @param head A `Head` element representing a fractal head.
      * @return The file title as derived from the head, or null if it yields none.
      */
    private String fileTitle( Node head ) throws TransformError {
        final String titlingExtract; // The relevant text extracted from the fractal head.
        if( "Division".equals( head.getParentNode().getLocalName() )) { // Then `head` is a divider.
            for( Node n = successor(head);;  n = successor(n) ) {
                if( n == null ) return null;
                if( "DivisionLabel".equals( n.getLocalName() )) {
                    titlingExtract = n.getTextContent();
                    break; }}}
        else { // Presumeably `head` is a file head or point head.
            head = head.cloneNode( /*deeply*/true ); /* So both preserving the original,
              and keeping the nodal scan that follows within the bounds of the isolated copy. */
            strip: for( Node p, n = successor(p = head);  n != null;  n = successor(p = n) ) {
                final String localName = n.getLocalName();
                if( "IndentBlind".equals( localName )) for( ;; ) { // Then remove `n` and all successors.
                    final Node s = successorAfter( n );
                    n.getParentNode().removeChild( n );
                    if( s == null ) break strip;
                    n = s; }
                if( "CommentAppender".equals( localName )
                 || "CommentBlock"   .equals( localName )) { // Then `n` is a comment carrier, remove it.
                    final Node c = n;
                    c.getParentNode().removeChild( c );
                    n = p; }} // Resuming from the predecessor of comment carrier `n`, now removed.
            titlingExtract = head.getTextContent(); }
        final StringBuilder b = clear(stringBuilder).append( titlingExtract );
        collapseWhitespace( b );
        return b.isEmpty() ? null : b.toString(); }



    private final Transformer identityTransformer; {
        Transformer t;
        try { t = TransformerFactory.newInstance().newTransformer(); }
        catch( TransformerConfigurationException x ) { throw new Unhandled( x ); }
        t.setOutputProperty( DOCTYPE_SYSTEM, "about:legacy-compat" ); // [DI]
        t.setOutputProperty( ENCODING, "UTF-8" );
        t.setOutputProperty( OMIT_XML_DECLARATION, "yes" );
        identityTransformer = t; }



    private final StreamResult imageFileOutput = new StreamResult();



    private final StringBuilder stringBuilder = new StringBuilder(
      /*initial capacity*/0x2000/*or 8192*/ );



    private final ReusableCursor sourceCursor; }



// NOTE
// ────
//   DI  `DOCTYPE` inclusion.  The would-be alternative of using, at an earlier stage, the likes of
//       `d.appendChild( d.getImplementation().createDocumentType( "html", null, "about:legacy-compat" ))`
//        in order to give the initial DOM document (here `d`) a `DOCTYPE` turns out not to suffice
//        because it has no effect on the output.



                                                   // Copyright © 2020-2022  Michael Allan.  Licence MIT.
