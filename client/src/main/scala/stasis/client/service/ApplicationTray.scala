package stasis.client.service

import dorkbox.systemTray.{MenuItem, SystemTray}

import java.awt.event.ActionEvent

trait ApplicationTray {
  def init(): Unit
  def shutdown(): Unit
}

object ApplicationTray {
  def apply(callbacks: Callbacks): ApplicationTray =
    ApplicationTray(callbacks = callbacks, systemTray = Option(SystemTray.get()))

  def apply(callbacks: Callbacks, systemTray: Option[SystemTray]): ApplicationTray =
    systemTray match {
      case Some(tray) => Default(callbacks = callbacks, tray = tray)
      case None       => NoOp()
    }

  class Default(callbacks: Callbacks, tray: SystemTray) extends ApplicationTray {
    @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
    override def init(): Unit = {
      val info = "stasis - Background Service"

      tray.setTooltip(info)
      tray.setStatus(info)
      tray.setImage(getClass.getResource("/assets/logo.png"))

      tray.getMenu.add(new MenuItem("Start UI", (_: ActionEvent) => callbacks.startUiService()))
      tray.getMenu.add(new MenuItem("Exit", (_: ActionEvent) => callbacks.terminateService()))
    }

    override def shutdown(): Unit = tray.shutdown()
  }

  object Default {
    def apply(callbacks: Callbacks, tray: SystemTray): Default = new Default(callbacks, tray)
  }

  class NoOp extends ApplicationTray {
    override def init(): Unit = ()
    override def shutdown(): Unit = ()
  }

  object NoOp {
    def apply(): NoOp = new NoOp()
  }

  final case class Callbacks(
    terminateService: () => Unit,
    startUiService: () => Unit
  )
}
