import com.google.inject.AbstractModule
import services.ExportRunner

class Module extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[ExportRunner]).asEagerSingleton()
  }

}
