(function () {
    let startY = 0;
    let startTarget = null;

    function activeModal() {
        return document.querySelector(".modal.show");
    }

    function closestScrollable(target, modal) {
        if (!(target instanceof Element) || !modal.contains(target)) {
            return null;
        }
        return target.closest(".modal-body, .modal-content");
    }

    function onTouchStart(event) {
        const modal = activeModal();
        if (!modal || event.touches.length !== 1) {
            return;
        }
        startY = event.touches[0].clientY;
        startTarget = event.target;
    }

    function onTouchMove(event) {
        const modal = activeModal();
        if (!modal || event.touches.length !== 1) {
            return;
        }

        const scrollContainer = closestScrollable(startTarget || event.target, modal);
        if (!scrollContainer) {
            event.preventDefault();
            return;
        }

        const deltaY = event.touches[0].clientY - startY;
        const canScroll = scrollContainer.scrollHeight > scrollContainer.clientHeight + 1;
        if (!canScroll) {
            event.preventDefault();
            return;
        }

        const atTop = scrollContainer.scrollTop <= 0;
        const atBottom = scrollContainer.scrollTop + scrollContainer.clientHeight >= scrollContainer.scrollHeight - 1;
        if ((atTop && deltaY > 0) || (atBottom && deltaY < 0)) {
            event.preventDefault();
        }
    }

    document.addEventListener("touchstart", onTouchStart, { passive: true, capture: true });
    document.addEventListener("touchmove", onTouchMove, { passive: false, capture: true });
})();
