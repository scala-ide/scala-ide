package scala.tools.eclipse.interpreter

import org.eclipse.ui.contexts.IContextService
import scala.tools.eclipse.ScalaSourceViewerConfiguration
import org.eclipse.ui.handlers.IHandlerService
import org.eclipse.core.commands.ExecutionEvent
import org.eclipse.core.commands.AbstractHandler
import org.eclipse.ui.part.IPageBookViewPage
import scala.collection.mutable.ListBuffer

// TODO: remove reference to eclipse internal classes!
import org.eclipse.debug.internal.ui.views.console.{ ProcessConsole, ProcessConsolePageParticipant }
import org.eclipse.ui.internal.console.IOConsoleViewer // TODO: remove reference to eclipse internal class!

import org.eclipse.ui.console.{ IConsole, TextConsole, IConsoleDocumentPartitioner, TextConsolePage, TextConsoleViewer }
import org.eclipse.debug.core.model.IProcess
import org.eclipse.debug.ui.console.IConsoleColorProvider
import org.eclipse.ui.console.{ IConsoleView, IConsolePageParticipant}
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.events.VerifyEvent
import org.eclipse.jface.text.{ IDocumentListener, DocumentEvent }

class ReplConsole(name: String, val process: IProcess, colorProvider: IConsoleColorProvider) 
	extends ProcessConsole(process, colorProvider) 
{
	
	var inputHistory: ListBuffer[String] = null
	
	protected override def computeName(): String = name
	
	override def createPage(view: IConsoleView): ReplConsolePage = {
		new ReplConsolePage(this, view)
	}
	
	def setHistoryList(list: ListBuffer[String]) = {
		inputHistory = list
	}
}

class ReplConsolePage(console: ReplConsole, view: IConsoleView) extends TextConsolePage(console, view) {
	
	protected override def createViewer(parent: Composite): ReplConsoleViewer = 
		new ReplConsoleViewer(parent, console)
}

class ReplConsoleViewer(parent: Composite, console: ReplConsole) extends IOConsoleViewer(parent, console) {
		
	val inputHistory = new ListBuffer[String]

	console.getDocument.addDocumentListener(docListener)
	console.setHistoryList(inputHistory) // TODO: yucky yucky no joy joy
	
	protected override def handleVerifyEvent(event: VerifyEvent) = {
		super.handleVerifyEvent(event)
//		println("handleVerifyEvent! text: " + event.text)
		val delimiters = getDocument.getLegalLineDelimiters()
		if (!delimiters.contains(event.text))
			inputHistory.append(event.text)
	}
	
	object docListener extends IDocumentListener {
		override def documentAboutToBeChanged(event: DocumentEvent) = { }
		override def documentChanged(event: DocumentEvent) = {
//			println("Document changed! " + event.getText)
			getTextWidget.setCaretOffset(getTextWidget.getCharCount)
		}
	}	
}

class ReplConsolePageParticipant extends IConsolePageParticipant {
	
	val HISTORY_UP = "scala.tools.eclipse.repl.historyUp"
	val CONTEXT_ID = "org.eclipse.debug.ui.console" // TODO: change!
	
	var page: IPageBookViewPage = null
	var console: ReplConsole = null
	
	override def init(page: IPageBookViewPage, console: IConsole) {
		this.page = page
		this.console = console.asInstanceOf[ReplConsole]
	}
	
	override def activated() {
		println("ReplConsolePageParticipant.activated!")
		val contextService = page.getSite.getService(classOf[IContextService]).asInstanceOf[IContextService]
		contextService.activateContext(CONTEXT_ID)
		val handlerService = page.getSite.getService(classOf[IHandlerService]).asInstanceOf[IHandlerService]
		handlerService.activateHandler(HISTORY_UP, historyUpHandler)
	}
	
	override def deactivated() { } // TODO: do something for deactivated
	override def dispose() { } // TODO: do something for dispose
	override def getAdapter(required: Class[_]): Object = null
	
	object historyUpHandler extends AbstractHandler {
		override def execute(event: ExecutionEvent) = {
//			println("Input history! : " + console.inputHistory)
			console.getDocument.replace(console.getDocument.getLength, 0, console.inputHistory.mkString)
			null
		}
	}
}