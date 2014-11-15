package scrollmenu

import org.scalajs.dom

import scalatags.JsDom.all._

case class Tree[T](value: T, children: Vector[Tree[T]])

case class MenuNode(frag: dom.HTMLElement, start: Int, end: Int)

/**
 * High performance scrollspy to work keep the left menu bar in sync.
 * Lots of sketchy imperative code in order to maximize performance.
 */
class ScrollSpy(structure: Tree[String], main: dom.HTMLElement){
  val (headers, domTrees) = {
    var i = 0
    def recurse(t: Tree[String], depth: Int): Tree[MenuNode] = {
      val curr =
        li(
          a(
            t.value,
            href:="#"+Controller.munge(t.value),
            cls:="menu-item"
          )
        )
      val originalI = i
      i += 1
      val children = t.children.map(recurse(_, depth + 1))
      Tree(
        MenuNode(
          curr(ul(paddingLeft := "15px",children.map(_.value.frag))).render,
          originalI,
          if (children.length > 0) children.map(_.value.end).max else originalI + 1
        ),
        children
      )
    }
    def offset(el: dom.HTMLElement, parent: dom.HTMLElement): Double = {
      if (el == parent) 0
      else el.offsetTop + offset(el.offsetParent.asInstanceOf[dom.HTMLElement], parent)
    }
    val headers = {
      val menuItems = {
        def rec(current: Tree[String]): Seq[String] = {
          current.value +: current.children.flatMap(rec)
        }
        rec(structure).tail
      }
      menuItems.map(Controller.munge)
        .map(dom.document.getElementById)
        .map(offset(_, main))
        .toVector
    }
    val domTrees = structure.children.map(recurse(_, 0))
    (headers, domTrees)
  }


  private[this] var scrolling = false
  def apply(threshold: => Double) = if (!scrolling){
    scrolling = true
    dom.setTimeout(() => start(threshold), 200)
  }
  private[this] def start(threshold: Double) = {
    scrolling = false
    def scroll(el: dom.HTMLElement) = {
      val rect = el.getBoundingClientRect()
      if (rect.top <= 0)
        el.scrollIntoView(true)
      else if (rect.bottom > dom.innerHeight)
        el.scrollIntoView(false)
    }
    def walkTree(tree: Tree[MenuNode]): Boolean = {
      val Tree(MenuNode(menuItem, start, end), children) = tree
      val before = headers(start) < threshold
      val after = (end >= headers.length) || headers(end) > threshold
      val win = before && after
      if (win){
        menuItem.classList.remove("hide")
        var winFound = false

        for(c <- tree.children){
          val newWinFound = walkTree(c)
          if (!winFound) c.value.frag.classList.add("selected")
          else c.value.frag.classList.remove("selected")
          winFound = winFound | newWinFound
        }
        if (!winFound) {
          // This means it's the leaf element, because it won but there
          // aren't any children which won, so it must be the actual leaf
          tree.children.foreach(_.value.frag.classList.remove("selected"))
          scroll(menuItem)
        }
        menuItem.children(0).classList.add("pure-menu-selected")
      }else{
        menuItem.children(0).classList.remove("pure-menu-selected")
        menuItem.classList.add("hide")
        menuItem.classList.remove("selected")
      }
      win
    }
    domTrees.map(walkTree)
  }


}