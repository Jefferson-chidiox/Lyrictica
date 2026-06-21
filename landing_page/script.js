document.addEventListener('DOMContentLoaded', () => {
    // Generate mock waves dynamically for the hero visualizer
    const visualizer = document.querySelector('.hero-visualizer-mock');
    visualizer.innerHTML = ''; // Clear static ones
    
    // We want roughly 50 bars to simulate high-fidelity FFT output
    const waveCount = 50;
    const colors = [
        'var(--accent-red)', 
        'var(--accent-purple)', 
        'var(--accent-blue)', 
        'var(--accent-green)'
    ];
    
    for (let i = 0; i < waveCount; i++) {
        const wave = document.createElement('div');
        wave.classList.add('mock-wave');
        
        // Randomize height, animation duration, and delay for natural feel
        const duration = 0.5 + Math.random() * 1.5;
        const delay = Math.random() * -2;
        
        // Split waves into frequency bands conceptually (Bass, Low-Mid, High-Mid, Treble)
        const bandRatio = i / waveCount;
        let colorIndex = 0;
        if (bandRatio > 0.75) colorIndex = 3;
        else if (bandRatio > 0.5) colorIndex = 2;
        else if (bandRatio > 0.25) colorIndex = 1;
        
        const color = colors[colorIndex];
        
        wave.style.animationDuration = `${duration}s`;
        wave.style.animationDelay = `${delay}s`;
        wave.style.background = color;
        wave.style.boxShadow = `0 -5px 15px ${color}`;
        wave.style.opacity = '0.8';
        
        // Slight height modulation based on band
        let baseHeight = 10;
        if (colorIndex === 0) baseHeight = 20; // Bass is typically stronger
        
        // We set inline style variables to allow CSS keyframes to use them if needed,
        // but simple CSS keyframes work fine with generic heights since we randomize duration.
        
        visualizer.appendChild(wave);
    }

    // Scroll reveal animation
    const revealElements = document.querySelectorAll('.fade-in-up');

    const revealOnScroll = () => {
        const windowHeight = window.innerHeight;
        const elementVisible = 100;
        
        revealElements.forEach(el => {
            const elementTop = el.getBoundingClientRect().top;
            if (elementTop < windowHeight - elementVisible) {
                el.classList.add('visible');
            }
        });
    };

    window.addEventListener('scroll', revealOnScroll);
    revealOnScroll(); // Trigger once on load
});
