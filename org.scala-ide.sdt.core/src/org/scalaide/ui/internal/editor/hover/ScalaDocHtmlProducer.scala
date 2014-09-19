package org.scalaide.ui.internal.editor.hover

import scala.tools.nsc.doc.html.HtmlPage
import scala.tools.nsc.doc.base._
import scala.tools.nsc.doc.base.comment._
import scala.reflect.internal.util.SourceFile
import org.eclipse.jface.internal.text.html.BrowserInformationControlInput
import scala.xml.NodeSeq
import scala.beans.BeanProperty
import scala.tools.nsc.interactive.Response
import scala.reflect.api.Position
import org.scalaide.core.compiler.IScalaPresentationCompiler
import org.eclipse.jdt.core.IJavaElement
import scala.reflect.internal.Flags
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jface.internal.text.html.BrowserInput

class ScalaDocHtmlProducer {

  private def bodiesToHtml(caption: String, bodies: List[Body]): NodeSeq =
    bodies match {
      case Nil => NodeSeq.Empty
      case _ =>
        <p>
          {
            val first = if (!caption.isEmpty) <h3>{ caption }</h3> else NodeSeq.Empty
            val last = bodies.flatMap(bodyToHtml).flatten
            first ++ last
          }
        </p>
    }

  private def bodyToHtml(body: Body): NodeSeq = body.blocks flatMap blockToHtml
  private def blockToHtml(block: comment.Block): NodeSeq = block match {
    case Title(in, _) => <h6>{ inlineToHtml(in) }</h6>
    case Paragraph(in) => <p>{ inlineToHtml(in) }</p>
    case Code(data) => <br/><pre><i>{ scala.xml.Text(data) }</i></pre><br/>
    case UnorderedList(items) => <ul>{ listItemsToHtml(items) }</ul>
    case OrderedList(items, listStyle) => <ol class={ listStyle }>{ listItemsToHtml(items) }</ol>
    case DefinitionList(items) =>
      <dl>{ items map { case (t, d) => <dt>{ inlineToHtml(t) }</dt><dd>{ blockToHtml(d) }</dd> } }</dl>
    case HorizontalRule() => <hr/>
  }

  private def listItemsToHtml(items: Seq[comment.Block]) =
    items.foldLeft(NodeSeq.Empty) { (xmlList, item) =>
      item match {
        case OrderedList(_, _) | UnorderedList(_) => // html requires sub ULs to be put into the last LI
          xmlList.init ++ <li>{ xmlList.last.child ++ blockToHtml(item) }</li>
        case Paragraph(inline) =>
          xmlList :+ <li>{ inlineToHtml(inline) }</li> // LIs are blocks, no need to use Ps
        case block =>
          xmlList :+ <li>{ blockToHtml(block) }</li>
      }
    }

  private def inlineToHtml(inl: Inline): NodeSeq = inl match {
    case Chain(items) => items flatMap inlineToHtml
    case Italic(in) => <i>{ inlineToHtml(in) }</i>
    case Bold(in) => <b>{ inlineToHtml(in) }</b>
    case Underline(in) => <u>{ inlineToHtml(in) }</u>
    case Superscript(in) => <sup>{ inlineToHtml(in) }</sup>
    case Subscript(in) => <sub>{ inlineToHtml(in) }</sub>
    case Link(raw, title) => <a href={ raw } target="_blank">{ inlineToHtml(title) }</a>
    case Monospace(in) => <code>{ inlineToHtml(in) }</code>
    case Text(text) => scala.xml.Text(text)
    case Summary(in) => inlineToHtml(in)
    case HtmlTag(tag) => scala.xml.Unparsed(tag)
    case EntityLink(in, _) => inlineToHtml(in)
  }

  private def htmlContents(header: String, comment: Comment): NodeSeq = {
    val headerHtml =
      if (header.isEmpty) NodeSeq.Empty
      else
        <h3>{ header }</h3>

    val mainHtml = List(
      bodyToHtml(comment.body),
      bodiesToHtml("Example" + (if (comment.example.length > 1) "s" else ""), comment.example),
      bodiesToHtml("Version", comment.version.toList),
      bodiesToHtml("Since", comment.since.toList),
      bodiesToHtml("Note", comment.note),
      bodiesToHtml("See also", comment.see),
      bodiesToHtml("To do", comment.todo),
      bodiesToHtml("Deprecated", comment.deprecated.toList),
      bodiesToHtml("Exceptions Thrown", comment.throws.toList.sortBy(_._1).map {
        case (name, body) => Body(Title(Text(name), 0) :: body.blocks.toList)
      })
    ).flatten

    headerHtml ++ mainHtml
  }

     def getBrowserInput(compiler: IScalaPresentationCompiler, javaProject: IJavaProject)(comment: Comment, sym: compiler.Symbol, header: String): Option[BrowserInput] = {
      import org.scalaide.core.compiler.IScalaPresentationCompiler.Implicits.RichResponse
      import compiler._
      val htmlOutput = asyncExec {
        <html>
          <body>
            { htmlContents(header, comment) }
          </body>
        </html>
      }.getOption()
      val javaElement = getJavaElement(sym, javaProject)
      htmlOutput.map { (htmlOutput) =>
        new BrowserInformationControlInput(null) {
          override def getHtml: String = htmlOutput.toString
          override def getInputElement: Object = javaElement
          override def getInputName: String = javaElement.map(_.getElementName()).getOrElse("")
        }
      }
    }
}